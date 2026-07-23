package com.archops.knowledge.architecture.service;

import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.architecture.ArchitectureMetrics;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitectureFact;
import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.dto.FactCreateRequest;
import com.archops.knowledge.architecture.dto.FactOpRequest;
import com.archops.knowledge.architecture.dto.PartitionDetailResponse;
import com.archops.knowledge.architecture.event.ArchitectureMergedEvent;
import com.archops.knowledge.architecture.repository.ArchitectureFactRepository;
import com.archops.knowledge.architecture.repository.ArchitectureProposalRepository;
import com.archops.knowledge.architecture.repository.ArchitectureRevisionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchitectureMergeEngine {

    private final ArchitecturePartitionService partitionService;
    private final ArchitectureRevisionRepository revisionRepository;
    private final ArchitectureFactRepository factRepository;
    private final ArchitectureProposalRepository proposalRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ArchitectureMetrics architectureMetrics;

    public ArchitectureMergeEngine(
            ArchitecturePartitionService partitionService,
            ArchitectureRevisionRepository revisionRepository,
            ArchitectureFactRepository factRepository,
            ArchitectureProposalRepository proposalRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            ArchitectureMetrics architectureMetrics) {
        this.partitionService = partitionService;
        this.revisionRepository = revisionRepository;
        this.factRepository = factRepository;
        this.proposalRepository = proposalRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.architectureMetrics = architectureMetrics;
    }

    @Transactional
    public PartitionDetailResponse mergeApprovedProposal(ArchitectureProposal proposal, Long reviewerId) {
        if (proposal == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROPOSAL_REQUIRED", "proposal 不能为空");
        }
        PartitionKeys.validate(proposal.getPartitionKey());
        ArchitecturePartition partition = partitionService.getOrCreate(proposal.getPartitionKey());

        ArchitectureRevision latest = revisionRepository
                .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .orElse(null);
        long currentVersion = latest != null ? latest.getVersion() : 0L;
        if (!Long.valueOf(currentVersion).equals(proposal.getBaseVersion())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ARCHITECTURE_VERSION_CONFLICT",
                    "合并冲突: expected baseVersion=" + currentVersion + ", got=" + proposal.getBaseVersion());
        }

        List<FactOpRequest> ops = parseFactOps(proposal.getFactOps());

        ArchitectureRevision revision = new ArchitectureRevision();
        revision.setPartitionId(partition.getId());
        revision.setVersion(currentVersion + 1);
        revision.setSummary(proposal.getSummary() != null
                ? proposal.getSummary()
                : (latest != null ? latest.getSummary() : null));
        revision.setBodyMd(latest != null ? latest.getBodyMd() : null);
        revision.setStructuredJson(latest != null ? latest.getStructuredJson() : "{}");
        revision.setCreatedBy(reviewerId);
        revision = revisionRepository.save(revision);

        for (FactOpRequest op : ops) {
            applyFactOp(partition.getId(), revision.getId(), op);
        }

        proposal.setStatus(ProposalStatus.MERGED);
        proposal.setReviewerId(reviewerId);
        if (proposal.getDecidedAt() == null) {
            proposal.setDecidedAt(Instant.now());
        }
        proposalRepository.save(proposal);

        auditService.record(new AuditService.AuditEntry(
                reviewerId,
                null,
                "architecture.merge",
                "architecture_proposal:" + proposal.getId(),
                partition.isHighImpact() || PartitionKeys.GLOBAL.equals(partition.getPartitionKey())
                        ? "HIGH"
                        : "MEDIUM",
                "SUCCESS",
                "{\"partitionKey\":\"" + proposal.getPartitionKey() + "\",\"version\":"
                        + revision.getVersion() + "}",
                null,
                null));

        eventPublisher.publishEvent(new ArchitectureMergedEvent(
                proposal.getPartitionKey(), revision.getVersion(), proposal.getId()));

        architectureMetrics.incrementMerged();

        return partitionService.toDetail(partition);
    }

    private void applyFactOp(Long partitionId, Long revisionId, FactOpRequest op) {
        String action = op.op() != null ? op.op().trim().toUpperCase(Locale.ROOT) : "";
        if ("ADD".equals(action)) {
            String provenance = op.provenance() != null ? op.provenance() : "{}";
            partitionService.validateActiveProvenance(provenance);
            FactCreateRequest req = new FactCreateRequest(
                    op.factType(),
                    op.subject(),
                    op.predicate(),
                    op.object(),
                    op.assetId(),
                    op.confidence(),
                    provenance);
            factRepository.save(partitionService.newFact(partitionId, revisionId, req));
            return;
        }
        if ("DEPRECATE".equals(action)) {
            List<ArchitectureFact> active = factRepository.findByPartitionIdAndStatus(partitionId, "active");
            for (ArchitectureFact fact : active) {
                if (matches(fact, op)) {
                    fact.setStatus("deprecated");
                    factRepository.save(fact);
                }
            }
            return;
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FACT_OP", "未知 fact op: " + op.op());
    }

    private static boolean matches(ArchitectureFact fact, FactOpRequest op) {
        return eq(fact.getFactType(), op.factType())
                && eq(fact.getSubject(), op.subject())
                && eq(fact.getPredicate(), op.predicate())
                && eq(fact.getObject(), op.object())
                && (op.assetId() == null || op.assetId().equals(fact.getAssetId()));
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    List<FactOpRequest> parseFactOps(String factOpsJson) {
        if (factOpsJson == null || factOpsJson.isBlank()) {
            return List.of();
        }
        try {
            List<FactOpRequest> ops = objectMapper.readValue(factOpsJson, new TypeReference<>() {});
            return ops != null ? ops : List.of();
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FACT_OPS", "fact_ops JSON 无效");
        }
    }

    String writeFactOps(List<FactOpRequest> ops) {
        try {
            return objectMapper.writeValueAsString(ops != null ? ops : new ArrayList<>());
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FACT_OPS", "fact_ops 序列化失败");
        }
    }
}
