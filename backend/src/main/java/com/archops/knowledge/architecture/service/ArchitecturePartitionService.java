package com.archops.knowledge.architecture.service;

import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.architecture.ArchitectureMetrics;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitectureFact;
import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import com.archops.knowledge.architecture.dto.FactCreateRequest;
import com.archops.knowledge.architecture.dto.FactResponse;
import com.archops.knowledge.architecture.dto.PartitionDetailResponse;
import com.archops.knowledge.architecture.dto.PartitionSummaryResponse;
import com.archops.knowledge.architecture.dto.RevisionWriteRequest;
import com.archops.knowledge.architecture.repository.ArchitectureFactRepository;
import com.archops.knowledge.architecture.repository.ArchitecturePartitionRepository;
import com.archops.knowledge.architecture.repository.ArchitectureRevisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchitecturePartitionService {

    private final ArchitecturePartitionRepository partitionRepository;
    private final ArchitectureRevisionRepository revisionRepository;
    private final ArchitectureFactRepository factRepository;
    private final AssetAclService assetAclService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ArchitectureMetrics architectureMetrics;

    public ArchitecturePartitionService(
            ArchitecturePartitionRepository partitionRepository,
            ArchitectureRevisionRepository revisionRepository,
            ArchitectureFactRepository factRepository,
            AssetAclService assetAclService,
            AuditService auditService,
            ObjectMapper objectMapper,
            ArchitectureMetrics architectureMetrics) {
        this.partitionRepository = partitionRepository;
        this.revisionRepository = revisionRepository;
        this.factRepository = factRepository;
        this.assetAclService = assetAclService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.architectureMetrics = architectureMetrics;
    }

    @Transactional(readOnly = true)
    public List<PartitionSummaryResponse> listPartitions() {
        return partitionRepository.findAll().stream().map(this::toSummary).toList();
    }

    /** Alias used by earlier stubs. */
    @Transactional(readOnly = true)
    public List<PartitionSummaryResponse> listSummaries() {
        return listPartitions();
    }

    @Transactional
    public ArchitecturePartition ensureGlobal() {
        return getOrCreate(PartitionKeys.GLOBAL);
    }

    @Transactional
    public ArchitecturePartition getOrCreate(String partitionKey) {
        PartitionKeys.validate(partitionKey);
        return partitionRepository.findByPartitionKey(partitionKey).orElseGet(() -> {
            ArchitecturePartition p = new ArchitecturePartition();
            p.setPartitionKey(partitionKey);
            p.setTitle(defaultTitle(partitionKey));
            p.setHighImpact(PartitionKeys.GLOBAL.equals(partitionKey));
            return partitionRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public PartitionDetailResponse getDetail(String partitionKey) {
        PartitionKeys.validate(partitionKey);
        ArchitecturePartition partition = partitionRepository.findByPartitionKey(partitionKey)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ARCHITECTURE_PARTITION_NOT_FOUND", "分区不存在: " + partitionKey));
        return toDetail(partition);
    }

    /** ACL-aware detail for non-admin callers. */
    @Transactional(readOnly = true)
    public PartitionDetailResponse getDetail(String partitionKey, Long userId, Collection<String> roles) {
        PartitionKeys.validate(partitionKey);
        if (!assetAclService.isAdmin(roles)) {
            assetAclService.requirePartitionAccess(userId, roles, partitionKey);
        }
        return getDetail(partitionKey);
    }

    @Transactional
    public PartitionDetailResponse adminWrite(String key, RevisionWriteRequest request, Long actorId) {
        PartitionKeys.validate(key);
        if (request.baseVersion() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BASE_VERSION_REQUIRED", "baseVersion 不能为空");
        }
        ArchitecturePartition partition = getOrCreate(key);
        ArchitectureRevision latest = revisionRepository
                .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .orElse(null);
        long currentVersion = latest != null ? latest.getVersion() : 0L;
        if (!request.baseVersion().equals(currentVersion)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ARCHITECTURE_VERSION_CONFLICT",
                    "分区版本冲突: expected baseVersion=" + currentVersion + ", got=" + request.baseVersion());
        }

        List<FactCreateRequest> facts = request.facts() != null ? request.facts() : List.of();
        for (FactCreateRequest fact : facts) {
            validateActiveProvenance(fact.provenanceJson());
        }

        ArchitectureRevision revision = new ArchitectureRevision();
        revision.setPartitionId(partition.getId());
        revision.setVersion(currentVersion + 1);
        revision.setSummary(request.summary());
        revision.setBodyMd(request.bodyMd());
        revision.setStructuredJson(request.structuredJson() != null ? request.structuredJson() : "{}");
        revision.setCreatedBy(actorId);
        revision = revisionRepository.save(revision);

        for (FactCreateRequest factReq : facts) {
            factRepository.save(newFact(partition.getId(), revision.getId(), factReq));
        }

        auditService.record(new AuditService.AuditEntry(
                actorId,
                null,
                "architecture.admin_write",
                "architecture_partition:" + key,
                PartitionKeys.GLOBAL.equals(key) || partition.isHighImpact() ? "HIGH" : "MEDIUM",
                "SUCCESS",
                "{\"version\":" + revision.getVersion() + ",\"factCount\":" + facts.size() + "}",
                null,
                null));

        return toDetail(partition);
    }

    @Transactional
    public PartitionDetailResponse rollback(String key, Long targetVersion, Long actorId) {
        PartitionKeys.validate(key);
        if (targetVersion == null || targetVersion <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_VERSION", "targetVersion 无效");
        }
        ArchitecturePartition partition = partitionRepository.findByPartitionKey(key)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ARCHITECTURE_PARTITION_NOT_FOUND", "分区不存在: " + key));

        ArchitectureRevision target = revisionRepository
                .findByPartitionIdAndVersion(partition.getId(), targetVersion)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ARCHITECTURE_REVISION_NOT_FOUND",
                        "目标版本不存在: " + targetVersion));

        ArchitectureRevision latest = revisionRepository
                .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .orElse(target);
        long fromVersion = latest.getVersion();

        ArchitectureRevision newRevision = new ArchitectureRevision();
        newRevision.setPartitionId(partition.getId());
        newRevision.setVersion(fromVersion + 1);
        newRevision.setSummary(target.getSummary());
        newRevision.setBodyMd(target.getBodyMd());
        newRevision.setStructuredJson(target.getStructuredJson());
        newRevision.setCreatedBy(actorId);
        newRevision = revisionRepository.save(newRevision);

        List<ArchitectureFact> active = factRepository.findByPartitionIdAndStatus(partition.getId(), "active");
        for (ArchitectureFact fact : active) {
            fact.setStatus("deprecated");
            factRepository.save(fact);
        }

        List<ArchitectureFact> snapshotFacts =
                factRepository.findByPartitionIdAndRevisionId(partition.getId(), target.getId());
        if (!snapshotFacts.isEmpty()) {
            for (ArchitectureFact old : snapshotFacts) {
                factRepository.save(copyFact(old, partition.getId(), newRevision.getId()));
            }
        } else {
            reapplyFactsFromStructuredJson(partition.getId(), newRevision.getId(), target.getStructuredJson());
        }

        auditService.record(new AuditService.AuditEntry(
                actorId,
                null,
                "architecture.rollback",
                "architecture_partition:" + key,
                "HIGH",
                "SUCCESS",
                "{\"fromVersion\":" + fromVersion + ",\"toVersion\":" + targetVersion
                        + ",\"newVersion\":" + newRevision.getVersion() + "}",
                null,
                null));

        architectureMetrics.incrementRollback();

        return toDetail(partition);
    }

    @Transactional(readOnly = true)
    public long currentVersion(String partitionKey) {
        ArchitecturePartition partition = partitionRepository.findByPartitionKey(partitionKey).orElse(null);
        if (partition == null) {
            return 0L;
        }
        return revisionRepository.findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .map(ArchitectureRevision::getVersion)
                .orElse(0L);
    }

    public void validateActiveProvenance(String provenanceJson) {
        if (provenanceJson == null || provenanceJson.isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROVENANCE_REQUIRED", "active 事实必须提供 provenance");
        }
        try {
            JsonNode node = objectMapper.readTree(provenanceJson);
            if (!node.isObject() || !node.fields().hasNext()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PROVENANCE_REQUIRED",
                        "provenance 必须是至少包含一个键的 JSON 对象");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROVENANCE_INVALID", "provenance JSON 无效");
        }
    }

    public PartitionDetailResponse toDetail(ArchitecturePartition partition) {
        ArchitectureRevision latest = revisionRepository
                .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .orElse(null);
        List<FactResponse> facts = factRepository
                .findByPartitionIdAndStatus(partition.getId(), "active")
                .stream()
                .map(this::toFactResponse)
                .toList();
        return new PartitionDetailResponse(
                partition.getId(),
                partition.getPartitionKey(),
                partition.getTitle(),
                partition.isHighImpact(),
                latest != null ? latest.getId() : null,
                latest != null ? latest.getVersion() : 0L,
                latest != null ? latest.getSummary() : null,
                latest != null ? latest.getBodyMd() : null,
                latest != null ? latest.getStructuredJson() : "{}",
                latest != null ? latest.getCreatedBy() : null,
                latest != null ? latest.getCreatedAt() : partition.getUpdatedAt(),
                facts);
    }

    public ArchitectureFact newFact(Long partitionId, Long revisionId, FactCreateRequest req) {
        ArchitectureFact fact = new ArchitectureFact();
        fact.setPartitionId(partitionId);
        fact.setRevisionId(revisionId);
        fact.setFactType(req.factType());
        fact.setSubject(req.subject());
        fact.setPredicate(req.predicate());
        fact.setObject(req.object());
        fact.setAssetId(req.assetId());
        fact.setConfidence(req.confidence());
        fact.setStatus("active");
        fact.setProvenanceJson(req.provenanceJson() != null ? req.provenanceJson() : "{}");
        return fact;
    }

    private ArchitectureFact copyFact(ArchitectureFact old, Long partitionId, Long revisionId) {
        ArchitectureFact fact = new ArchitectureFact();
        fact.setPartitionId(partitionId);
        fact.setRevisionId(revisionId);
        fact.setFactType(old.getFactType());
        fact.setSubject(old.getSubject());
        fact.setPredicate(old.getPredicate());
        fact.setObject(old.getObject());
        fact.setAssetId(old.getAssetId());
        fact.setConfidence(old.getConfidence());
        fact.setStatus("active");
        fact.setProvenanceJson(old.getProvenanceJson());
        return fact;
    }

    private void reapplyFactsFromStructuredJson(Long partitionId, Long revisionId, String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            JsonNode factsNode = root.has("facts") ? root.get("facts") : root;
            if (!factsNode.isArray()) {
                return;
            }
            for (JsonNode node : factsNode) {
                if (!node.isObject()) {
                    continue;
                }
                String factType = text(node, "factType", "fact_type");
                String subject = text(node, "subject");
                String predicate = text(node, "predicate");
                String object = text(node, "object");
                if (factType == null || subject == null || predicate == null || object == null) {
                    continue;
                }
                String provenance = node.has("provenanceJson")
                        ? node.get("provenanceJson").toString()
                        : (node.has("provenance") ? node.get("provenance").toString() : "{\"restored\":true}");
                if ("{}".equals(provenance) || provenance.isBlank()) {
                    provenance = "{\"restored\":true}";
                }
                FactCreateRequest req = new FactCreateRequest(
                        factType,
                        subject,
                        predicate,
                        object,
                        node.has("assetId") && node.get("assetId").canConvertToLong()
                                ? node.get("assetId").asLong()
                                : null,
                        node.has("confidence") && node.get("confidence").isNumber()
                                ? node.get("confidence").asDouble()
                                : null,
                        provenance);
                factRepository.save(newFact(partitionId, revisionId, req));
            }
        } catch (Exception ignored) {
            // structured_json may not contain facts
        }
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name).asText();
            }
        }
        return null;
    }

    private PartitionSummaryResponse toSummary(ArchitecturePartition partition) {
        Long version = revisionRepository
                .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .map(ArchitectureRevision::getVersion)
                .orElse(0L);
        long activeCount = factRepository.countByPartitionIdAndStatus(partition.getId(), "active");
        return new PartitionSummaryResponse(
                partition.getId(),
                partition.getPartitionKey(),
                partition.getTitle(),
                partition.isHighImpact(),
                version,
                activeCount);
    }

    private FactResponse toFactResponse(ArchitectureFact f) {
        return new FactResponse(
                f.getId(),
                f.getFactType(),
                f.getSubject(),
                f.getPredicate(),
                f.getObject(),
                f.getAssetId(),
                f.getConfidence(),
                f.getStatus(),
                f.getProvenanceJson(),
                f.getRevisionId(),
                f.getCreatedAt());
    }

    private static String defaultTitle(String partitionKey) {
        if (PartitionKeys.GLOBAL.equals(partitionKey)) {
            return "Global architecture";
        }
        if (partitionKey.startsWith("group:")) {
            return "Group " + partitionKey.substring(6);
        }
        if (partitionKey.startsWith("asset:")) {
            return "Asset " + partitionKey.substring(6);
        }
        return partitionKey;
    }
}
