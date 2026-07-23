package com.archops.knowledge.architecture.service;

import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.architecture.ArchitectureProperties;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.dto.FactOpRequest;
import com.archops.knowledge.architecture.dto.ProposalCreateRequest;
import com.archops.knowledge.architecture.dto.ProposalDecideRequest;
import com.archops.knowledge.architecture.dto.ProposalResponse;
import com.archops.knowledge.architecture.repository.ArchitecturePartitionRepository;
import com.archops.knowledge.architecture.repository.ArchitectureProposalRepository;
import com.archops.user.domain.Role;
import com.archops.user.domain.User;
import com.archops.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchitectureProposalService {

    private final ArchitectureProposalRepository proposalRepository;
    private final ArchitecturePartitionRepository partitionRepository;
    private final ArchitecturePartitionService partitionService;
    private final ArchitectureMergeEngine mergeEngine;
    private final ArchitectureProperties properties;
    private final UserRepository userRepository;
    private final AssetAclService assetAclService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ArchitectureProposalService(
            ArchitectureProposalRepository proposalRepository,
            ArchitecturePartitionRepository partitionRepository,
            ArchitecturePartitionService partitionService,
            ArchitectureMergeEngine mergeEngine,
            ArchitectureProperties properties,
            UserRepository userRepository,
            AssetAclService assetAclService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.proposalRepository = proposalRepository;
        this.partitionRepository = partitionRepository;
        this.partitionService = partitionService;
        this.mergeEngine = mergeEngine;
        this.properties = properties;
        this.userRepository = userRepository;
        this.assetAclService = assetAclService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProposalResponse create(ProposalCreateRequest request, Long requesterId) {
        PartitionKeys.validate(request.partitionKey());
        assetAclService.requirePartitionAccess(requesterId, rolesOf(requesterId), request.partitionKey());
        partitionService.getOrCreate(request.partitionKey());

        List<FactOpRequest> ops = resolveFactOps(request);
        String factOpsJson = mergeEngine.writeFactOps(ops);
        String evidenceJson = request.evidenceJson() != null && !request.evidenceJson().isBlank()
                ? request.evidenceJson()
                : "[]";

        ArchitectureProposal proposal = new ArchitectureProposal();
        proposal.setPartitionKey(request.partitionKey());
        proposal.setSummary(request.summary());
        proposal.setDiffJson("{}");
        proposal.setFactOps(factOpsJson);
        proposal.setEvidence(evidenceJson);
        proposal.setRisk(request.risk());
        proposal.setConfidence(request.confidence());
        proposal.setRequesterId(requesterId);
        proposal.setConversationId(request.conversationId());
        proposal.setRelatedApprovalId(request.relatedApprovalId());
        proposal.setBaseVersion(request.baseVersion());
        proposal.setStatus(ProposalStatus.PENDING_REVIEW);

        boolean autoMerged = false;
        if (properties.getAutoMerge().isEnabled() && canAutoMerge(ops, evidenceJson, request.confidence())) {
            proposal.setStatus(ProposalStatus.AUTO_MERGED);
            proposal.setReviewerId(requesterId);
            proposal.setDecidedAt(Instant.now());
            proposal = proposalRepository.save(proposal);
            // Merge engine expects APPROVED-like merge; treat AUTO_MERGED as ready to apply
            mergeEngine.mergeApprovedProposal(proposal, requesterId);
            // merge sets MERGED; restore AUTO_MERGED semantics if desired — keep MERGED as final SSOT state
            autoMerged = true;
            proposal = proposalRepository.findById(proposal.getId()).orElse(proposal);
            if (proposal.getStatus() == ProposalStatus.MERGED) {
                proposal.setStatus(ProposalStatus.AUTO_MERGED);
                proposal = proposalRepository.save(proposal);
            }
        } else {
            proposal = proposalRepository.save(proposal);
        }

        auditService.record(new AuditService.AuditEntry(
                requesterId,
                null,
                autoMerged ? "architecture.proposal.auto_merge" : "architecture.proposal.create",
                "architecture_proposal:" + proposal.getId(),
                "MEDIUM",
                "SUCCESS",
                "{\"partitionKey\":\"" + proposal.getPartitionKey() + "\",\"status\":\""
                        + proposal.getStatus() + "\"}",
                null,
                null));

        return toResponse(proposal);
    }

    /**
     * Create a proposal from the propose_architecture_update agent tool.
     * Returns the persisted entity so callers can read the id.
     */
    @Transactional
    public ArchitectureProposal createFromTool(
            String partitionKey,
            String summary,
            List<Map<String, Object>> facts,
            String evidenceJson,
            Long requesterId,
            Long conversationId,
            ProposalStatus status,
            String risk,
            Double confidence) {
        PartitionKeys.validate(partitionKey);
        assetAclService.requirePartitionAccess(requesterId, rolesOf(requesterId), partitionKey);
        partitionService.getOrCreate(partitionKey);

        List<FactOpRequest> ops = new ArrayList<>();
        if (facts != null) {
            for (Map<String, Object> fact : facts) {
                ops.add(new FactOpRequest(
                        "ADD",
                        str(fact.get("factType"), "ROLE"),
                        str(fact.get("subject"), "unknown"),
                        str(fact.get("predicate"), "is"),
                        str(fact.get("object"), "unknown"),
                        asLong(fact.get("assetId")),
                        asDouble(fact.get("confidence")),
                        null));
            }
        }

        long baseVersion = partitionService.currentVersion(partitionKey);
        ProposalCreateRequest request = new ProposalCreateRequest(
                partitionKey,
                summary,
                mergeEngine.writeFactOps(ops),
                ops,
                evidenceJson != null ? evidenceJson : "[]",
                risk,
                confidence,
                conversationId,
                null,
                baseVersion);

        // Bypass nested ACL re-check by constructing directly when status != PENDING default path
        if (status == null || status == ProposalStatus.PENDING_REVIEW) {
            ProposalResponse response = create(request, requesterId);
            return findOrThrow(response.id());
        }

        ArchitectureProposal proposal = new ArchitectureProposal();
        proposal.setPartitionKey(partitionKey);
        proposal.setSummary(summary);
        proposal.setDiffJson("{}");
        proposal.setFactOps(mergeEngine.writeFactOps(ops));
        proposal.setEvidence(evidenceJson != null ? evidenceJson : "[]");
        proposal.setRisk(risk);
        proposal.setConfidence(confidence);
        proposal.setRequesterId(requesterId);
        proposal.setConversationId(conversationId);
        proposal.setBaseVersion(baseVersion);
        proposal.setStatus(status);
        return proposalRepository.save(proposal);
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> list(ProposalStatus status, String partitionKey) {
        List<ArchitectureProposal> proposals;
        if (status != null && partitionKey != null && !partitionKey.isBlank()) {
            proposals = proposalRepository.findByStatusAndPartitionKeyOrderByCreatedAtDesc(status, partitionKey);
        } else if (status != null) {
            proposals = proposalRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if (partitionKey != null && !partitionKey.isBlank()) {
            proposals = proposalRepository.findByPartitionKeyOrderByCreatedAtDesc(partitionKey);
        } else {
            proposals = proposalRepository.findAllByOrderByCreatedAtDesc();
        }
        return proposals.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProposalResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ProposalResponse decide(
            Long id,
            ProposalDecideRequest request,
            Long reviewerId,
            Collection<? extends GrantedAuthority> authorities) {
        ArchitectureProposal proposal = findOrThrow(id);
        if (proposal.getStatus() != ProposalStatus.PENDING_REVIEW) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "PROPOSAL_NOT_PENDING", "提案不在待审状态");
        }
        if (reviewerId.equals(proposal.getRequesterId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "PROPOSAL_SELF_REVIEW", "批准者不能与提案者相同");
        }

        String decision = request.decision() != null
                ? request.decision().trim().toUpperCase(Locale.ROOT)
                : "";
        boolean approve = "APPROVE".equals(decision);
        boolean reject = "REJECT".equals(decision);
        if (!approve && !reject) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_DECISION", "decision 必须是 APPROVE 或 REJECT");
        }

        ArchitecturePartition partition = partitionRepository
                .findByPartitionKey(proposal.getPartitionKey())
                .orElse(null);
        boolean highImpact = PartitionKeys.GLOBAL.equals(proposal.getPartitionKey())
                || (partition != null && partition.isHighImpact());
        if (approve && highImpact && !hasAdmin(reviewerId, authorities)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_REQUIRED",
                    "高影响 / global 分区提案需要 ADMIN 批准");
        }

        proposal.setReviewerId(reviewerId);
        proposal.setDecidedAt(Instant.now());
        if (reject) {
            proposal.setStatus(ProposalStatus.REJECTED);
            if (request.comment() != null && !request.comment().isBlank()) {
                proposal.setDiffJson("{\"comment\":"
                        + quoteJson(request.comment()) + "}");
            }
            proposalRepository.save(proposal);
            auditService.record(new AuditService.AuditEntry(
                    reviewerId,
                    null,
                    "architecture.proposal.reject",
                    "architecture_proposal:" + proposal.getId(),
                    "MEDIUM",
                    "SUCCESS",
                    request.comment() != null ? request.comment() : "",
                    null,
                    null));
            return toResponse(proposal);
        }

        proposal.setStatus(ProposalStatus.APPROVED);
        proposalRepository.save(proposal);
        mergeEngine.mergeApprovedProposal(proposal, reviewerId);
        proposal = findOrThrow(id);

        auditService.record(new AuditService.AuditEntry(
                reviewerId,
                null,
                "architecture.proposal.approve",
                "architecture_proposal:" + proposal.getId(),
                highImpact ? "HIGH" : "MEDIUM",
                "SUCCESS",
                request.comment() != null ? request.comment() : "",
                null,
                null));
        return toResponse(proposal);
    }

    private boolean canAutoMerge(List<FactOpRequest> ops, String evidenceJson, Double proposalConfidence) {
        ArchitectureProperties.AutoMerge cfg = properties.getAutoMerge();
        if (ops == null || ops.isEmpty()) {
            return false;
        }
        Set<String> allowed = cfg.getAllowedFactTypes().stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        for (FactOpRequest op : ops) {
            if (!"ADD".equalsIgnoreCase(op.op())) {
                return false;
            }
            if (!allowed.contains(op.factType() != null ? op.factType().toUpperCase(Locale.ROOT) : "")) {
                return false;
            }
            double conf = op.confidence() != null
                    ? op.confidence()
                    : (proposalConfidence != null ? proposalConfidence : 0.0);
            if (conf < cfg.getMinConfidence()) {
                return false;
            }
            if (cfg.isRequireAssetId() && op.assetId() == null) {
                return false;
            }
            if (!evidenceComplete(op, evidenceJson, cfg)) {
                return false;
            }
        }
        return true;
    }

    private boolean evidenceComplete(
            FactOpRequest op, String evidenceJson, ArchitectureProperties.AutoMerge cfg) {
        JsonNode provenance = parseObjectOrEmpty(op.provenance());
        JsonNode evidenceRoot = parseJsonOrEmpty(evidenceJson);

        boolean hasCommand = hasField(provenance, "command") || evidenceHasField(evidenceRoot, "command");
        boolean hasStdoutHash = hasField(provenance, "stdoutHash")
                || hasField(provenance, "stdout_hash")
                || evidenceHasField(evidenceRoot, "stdoutHash")
                || evidenceHasField(evidenceRoot, "stdout_hash");
        boolean hasAssetId = op.assetId() != null
                || hasField(provenance, "assetId")
                || hasField(provenance, "asset_id")
                || evidenceHasField(evidenceRoot, "assetId")
                || evidenceHasField(evidenceRoot, "asset_id");

        if (cfg.isRequireCommand() && !hasCommand) {
            return false;
        }
        if (cfg.isRequireStdoutHash() && !hasStdoutHash) {
            return false;
        }
        if (cfg.isRequireAssetId() && !hasAssetId) {
            return false;
        }
        return true;
    }

    private boolean evidenceHasField(JsonNode root, String field) {
        if (root == null || root.isNull()) {
            return false;
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                if (hasField(item, field)) {
                    return true;
                }
            }
            return false;
        }
        return hasField(root, field);
    }

    private static boolean hasField(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.has(field) || node.get(field).isNull()) {
            return false;
        }
        String text = node.get(field).asText("");
        return !text.isBlank();
    }

    private JsonNode parseObjectOrEmpty(String json) {
        JsonNode node = parseJsonOrEmpty(json);
        return node.isObject() ? node : objectMapper.createObjectNode();
    }

    private JsonNode parseJsonOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createArrayNode();
        }
    }

    private List<FactOpRequest> resolveFactOps(ProposalCreateRequest request) {
        if (request.factOps() != null && !request.factOps().isEmpty()) {
            return request.factOps();
        }
        return mergeEngine.parseFactOps(request.factOpsJson());
    }

    private boolean hasAdmin(Long reviewerId, Collection<? extends GrantedAuthority> authorities) {
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a != null && "ROLE_ADMIN".equals(a.getAuthority())) {
                    return true;
                }
            }
        }
        User user = userRepository.findById(reviewerId).orElse(null);
        if (user == null || user.getRoles() == null) {
            return false;
        }
        for (Role role : user.getRoles()) {
            if (role != null && "ADMIN".equalsIgnoreCase(role.getName())) {
                return true;
            }
        }
        return false;
    }

    private ArchitectureProposal findOrThrow(Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "提案不存在"));
    }

    private ProposalResponse toResponse(ArchitectureProposal p) {
        return new ProposalResponse(
                p.getId(),
                p.getPartitionKey(),
                p.getStatus(),
                p.getSummary(),
                p.getDiffJson(),
                p.getFactOps(),
                p.getEvidence(),
                p.getRisk(),
                p.getConfidence(),
                p.getRequesterId(),
                p.getReviewerId(),
                p.getConversationId(),
                p.getBaseVersion(),
                p.getRelatedApprovalId(),
                p.getCreatedAt(),
                p.getDecidedAt());
    }

    private String quoteJson(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "\"\"";
        }
    }

    private Collection<String> rolesOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRepository.findById(userId)
                .map(User::getRoles)
                .orElse(Set.of())
                .stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    private static String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }
}
