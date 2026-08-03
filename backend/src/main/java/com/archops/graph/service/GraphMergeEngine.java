package com.archops.graph.service;

import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.graph.changeset.GraphChangeSet;
import com.archops.graph.config.GraphProperties;
import com.archops.graph.domain.GraphMeta;
import com.archops.graph.domain.GraphRelType;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.event.ArchitectureMergedEvent;
import com.archops.knowledge.architecture.repository.ArchitectureProposalRepository;
import com.archops.knowledge.architecture.repository.ArchitectureRevisionRepository;
import com.archops.knowledge.architecture.service.ArchitecturePartitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Locale;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merge GraphChangeSet: PG anchors → Neo4j GraphOps → PG side effects / version bump.
 *
 * <p>If PG fails after Neo4j commit, best-effort reverse Neo4j ops and mark MERGE_FAILED.
 */
@Service
public class GraphMergeEngine {

    private static final Logger log = LoggerFactory.getLogger(GraphMergeEngine.class);

    private final GraphProperties graphProperties;
    private final GraphVersionService graphVersionService;
    private final Driver neo4jDriver;
    private final GraphOpApplier graphOpApplier;
    private final GraphPgAnchorService graphPgAnchorService;
    private final GraphProposalStatusService proposalStatusService;
    private final GraphConnectPathService graphConnectPathService;
    private final ArchitecturePartitionService partitionService;
    private final ArchitectureRevisionRepository revisionRepository;
    private final ArchitectureProposalRepository proposalRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public GraphMergeEngine(
            GraphProperties graphProperties,
            GraphVersionService graphVersionService,
            Driver neo4jDriver,
            GraphOpApplier graphOpApplier,
            GraphPgAnchorService graphPgAnchorService,
            GraphProposalStatusService proposalStatusService,
            GraphConnectPathService graphConnectPathService,
            ArchitecturePartitionService partitionService,
            ArchitectureRevisionRepository revisionRepository,
            ArchitectureProposalRepository proposalRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.graphProperties = graphProperties;
        this.graphVersionService = graphVersionService;
        this.neo4jDriver = neo4jDriver;
        this.graphOpApplier = graphOpApplier;
        this.graphPgAnchorService = graphPgAnchorService;
        this.proposalStatusService = proposalStatusService;
        this.graphConnectPathService = graphConnectPathService;
        this.partitionService = partitionService;
        this.revisionRepository = revisionRepository;
        this.proposalRepository = proposalRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public long mergeApprovedProposal(ArchitectureProposal proposal, Long actorId) {
        if (proposal == null || !proposal.hasGraphChangeSet()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "GRAPH_CHANGESET_REQUIRED", "提案缺少 Graph ChangeSet");
        }
        Driver driver = neo4jDriver;

        GraphChangeSet changeSet = parseChangeSet(proposal.getChangeSet());
        validateStatic(changeSet);
        if (changeSet.baseGraphVersion() != null
                && proposal.getBaseGraphVersion() != null
                && !changeSet.baseGraphVersion().equals(proposal.getBaseGraphVersion())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "GRAPH_BASE_VERSION_MISMATCH",
                    "ChangeSet baseGraphVersion 与提案不一致: changeSet="
                            + changeSet.baseGraphVersion()
                            + ", proposal="
                            + proposal.getBaseGraphVersion());
        }

        GraphMeta meta = graphVersionService.lockGlobal();
        long expected = proposal.getBaseGraphVersion() != null ? proposal.getBaseGraphVersion() : 0L;
        if (expected != meta.getGraphVersion()) {
            proposalStatusService.markConflict(proposal.getId(), actorId, expected, meta.getGraphVersion());
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "GRAPH_VERSION_CONFLICT",
                    "图版本冲突: expected=" + expected + ", actual=" + meta.getGraphVersion());
        }

        GraphTempBinder binder = new GraphTempBinder();
        graphPgAnchorService.prepareNodeCreates(
                changeSet.ops(), binder, proposal.getRequesterId());

        boolean neo4jCommitted = false;
        GraphApplyJournal applyJournal = null;
        try (Session session = driver.session(SessionConfig.forDatabase(graphProperties.getDatabase()))) {
            try {
                applyJournal = graphOpApplier.applyAll(session, changeSet.ops(), binder, proposal.getId());
                neo4jCommitted = true;
            } catch (BusinessException ex) {
                proposalStatusService.markMergeFailed(proposal.getId(), actorId, "NEO4J", ex.getMessage());
                throw ex;
            } catch (Exception ex) {
                log.error("Neo4j GraphOp apply failed for proposal {}", proposal.getId(), ex);
                proposalStatusService.markMergeFailed(proposal.getId(), actorId, "NEO4J", ex.getMessage());
                throw new BusinessException(
                        HttpStatus.BAD_GATEWAY, "NEO4J_WRITE_FAILED", "Neo4j 写入失败: " + ex.getMessage());
            }

            try {
                graphPgAnchorService.applySideEffects(
                        changeSet.pgSideEffects(),
                        binder,
                        actorId,
                        proposal.getId(),
                        proposal.getRequesterId());
                graphPgAnchorService.applyNodeSoftDeletes(changeSet.ops(), binder, actorId);
                graphPgAnchorService.syncProjections(changeSet.ops(), binder);
                syncCredentialFlags(changeSet, binder);

                String scopeKey = PartitionKeys.normalize(proposal.getPartitionKey());
                var partition = partitionService.getOrCreate(scopeKey);
                long docVersion = revisionRepository
                        .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                        .map(r -> r.getVersion() + 1)
                        .orElse(1L);

                long newGraphVersion = graphVersionService.bump(meta, null);

                ArchitectureRevision revision = new ArchitectureRevision();
                revision.setPartitionId(partition.getId());
                revision.setVersion(docVersion);
                revision.setGraphVersion(newGraphVersion);
                revision.setChangeSetId(changeSet.changeSetId());
                revision.setProposalId(proposal.getId());
                revision.setSummary(proposal.getSummary());
                revision.setBodyMd("Graph ChangeSet merge (ops=" + changeSet.ops().size() + ")");
                revision.setStructuredJson(proposal.getChangeSet());
                revision.setCreatedBy(actorId);
                revisionRepository.save(revision);

                proposal.setStatus(ProposalStatus.MERGED);
                proposal.setMergedGraphVersion(newGraphVersion);
                proposal.setReviewerId(actorId);
                proposal.setDecidedAt(Instant.now());
                proposal.setConflictDetail(null);
                proposalRepository.save(proposal);

                auditService.record(new AuditService.AuditEntry(
                        actorId,
                        null,
                        "graph.proposal.merge",
                        "architecture_proposal:" + proposal.getId(),
                        "HIGH",
                        "SUCCESS",
                        "{\"graphVersion\":" + newGraphVersion + ",\"ops\":" + changeSet.ops().size() + "}",
                        null,
                        null));

                eventPublisher.publishEvent(new ArchitectureMergedEvent(scopeKey, docVersion, proposal.getId()));

                log.info(
                        "Graph merge complete proposal={} graphVersion={} ops={}",
                        proposal.getId(),
                        newGraphVersion,
                        changeSet.ops().size());
                return newGraphVersion;
            } catch (RuntimeException pgEx) {
                log.error("PG side of graph merge failed after Neo4j commit; compensating", pgEx);
                try {
                    graphOpApplier.reverseAll(session, applyJournal);
                } catch (Exception reverseEx) {
                    log.error("Neo4j compensation failed for proposal {}", proposal.getId(), reverseEx);
                }
                proposalStatusService.markMergeFailed(proposal.getId(), actorId, "PG", pgEx.getMessage());
                if (pgEx instanceof BusinessException be) {
                    throw be;
                }
                throw new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "GRAPH_MERGE_PG_FAILED",
                        "图合并 PG 阶段失败（已尝试回滚 Neo4j）: " + pgEx.getMessage());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (neo4jCommitted) {
                log.error("Unexpected error after Neo4j commit for proposal {}", proposal.getId(), ex);
            }
            throw ex;
        }
    }

    private void syncCredentialFlags(GraphChangeSet changeSet, GraphTempBinder binder) {
        if (changeSet.pgSideEffects() == null) {
            return;
        }
        for (GraphChangeSet.PgSideEffect effect : changeSet.pgSideEffects()) {
            if (effect == null || effect.effect() == null) {
                continue;
            }
            String type = effect.effect().trim().toUpperCase(Locale.ROOT);
            Long assetId = effect.pgAssetId();
            if (assetId == null && effect.tempId() != null) {
                try {
                    assetId = binder.requireTemp(effect.tempId()).pgAssetId();
                } catch (BusinessException ignored) {
                    continue;
                }
            }
            if (assetId == null) {
                continue;
            }
            if ("CREDENTIAL_UPSERT_REF".equals(type)) {
                graphConnectPathService.markHasCredential(assetId, true);
            } else if ("CREDENTIAL_SOFT_DELETE".equals(type)) {
                graphConnectPathService.markHasCredential(assetId, false);
            }
        }
    }

    private GraphChangeSet parseChangeSet(String json) {
        try {
            GraphChangeSet cs = objectMapper.readValue(json, GraphChangeSet.class);
            if (cs == null || cs.isEmpty()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "GRAPH_CHANGESET_EMPTY", "ChangeSet ops 为空");
            }
            return cs;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "GRAPH_CHANGESET_INVALID", "ChangeSet JSON 无效: " + ex.getMessage());
        }
    }

    private void validateStatic(GraphChangeSet changeSet) {
        if (changeSet.schemaVersion() > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "GRAPH_CHANGESET_VERSION",
                    "不支持的 ChangeSet schemaVersion=" + changeSet.schemaVersion());
        }
        for (GraphChangeSet.GraphOp op : changeSet.ops()) {
            if (op.op() == null || op.op().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_OP_INVALID", "op 不能为空");
            }
            String kind = op.op().trim().toUpperCase(Locale.ROOT);
            switch (kind) {
                case "NODE_CREATE", "NODE_UPDATE", "NODE_SOFT_DELETE",
                        "REL_CREATE", "REL_UPDATE", "REL_DELETE",
                        "TAG_ADD", "TAG_REMOVE" -> {
                }
                default -> throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "GRAPH_OP_UNKNOWN", "未知 GraphOp: " + op.op());
            }
            if (("REL_CREATE".equals(kind) || "REL_UPDATE".equals(kind))
                    && op.type() != null
                    && !op.type().isBlank()) {
                try {
                    GraphRelType.from(op.type());
                } catch (Exception ex) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "GRAPH_REL_TYPE_INVALID", "非法边类型: " + op.type());
                }
            }
        }
    }
}
