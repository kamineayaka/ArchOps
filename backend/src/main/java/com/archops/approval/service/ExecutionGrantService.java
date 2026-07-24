package com.archops.approval.service;

import com.archops.approval.ApprovalProperties;
import com.archops.approval.domain.Approval;
import com.archops.approval.domain.ExecutionGrant;
import com.archops.approval.domain.RiskLevel;
import com.archops.approval.repository.ExecutionGrantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session-scoped execution grants. Covers EXECUTION tools only — never
 * {@code propose_architecture_update} (Architecture Proposal gate must not be bypassed).
 */
@Service
public class ExecutionGrantService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionGrantService.class);

    /** Tools eligible for session grants. Knowledge proposal tools are intentionally excluded. */
    private static final Set<String> GRANTABLE_TOOLS =
            Set.of("ssh_exec", "list_assets", "db_ping", "k8s_get");

    private static final String PROPOSE_ARCHITECTURE_UPDATE = "propose_architecture_update";

    private final ExecutionGrantRepository grantRepository;
    private final ApprovalProperties properties;
    private final ObjectMapper objectMapper;

    public ExecutionGrantService(
            ExecutionGrantRepository grantRepository,
            ApprovalProperties properties,
            ObjectMapper objectMapper) {
        this.grantRepository = grantRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isGrantableTool(String toolName) {
        return toolName != null
                && GRANTABLE_TOOLS.contains(toolName)
                && !PROPOSE_ARCHITECTURE_UPDATE.equals(toolName);
    }

    public boolean canGrantRisk(RiskLevel risk) {
        if (risk == null) {
            return false;
        }
        if (risk == RiskLevel.HIGH) {
            return properties.isAllowHighGrants();
        }
        return true;
    }

    /**
     * Create a grant from an approved decision when the requester opted into
     * "remember for this session".
     *
     * @return empty if tool/risk/conversation are not grant-eligible
     */
    @Transactional
    public Optional<ExecutionGrant> createFromApproval(Approval approval) {
        if (approval == null) {
            return Optional.empty();
        }
        if (!canGrantRisk(approval.getRiskLevel())) {
            log.debug("Skip grant for approval {}: HIGH not grantable", approval.getId());
            return Optional.empty();
        }

        PayloadFields fields = parsePayload(approval.getPayload());
        if (fields.conversationId() == null) {
            log.debug("Skip grant for approval {}: no conversationId", approval.getId());
            return Optional.empty();
        }
        if (!isGrantableTool(fields.toolName())) {
            log.debug("Skip grant for approval {}: tool {} not grantable", approval.getId(), fields.toolName());
            return Optional.empty();
        }

        ExecutionGrant grant = new ExecutionGrant();
        grant.setUserId(approval.getRequesterId());
        grant.setConversationId(fields.conversationId());
        grant.setToolName(fields.toolName());
        grant.setAssetId(fields.assetId());
        grant.setRiskLevel(approval.getRiskLevel());
        grant.setPattern(fields.pattern());
        grant.setCreatedAt(Instant.now());
        grant.setExpiresAt(Instant.now().plus(properties.getGrantTtlMinutes(), ChronoUnit.MINUTES));
        grant.setCreatedByApprovalId(approval.getId());
        return Optional.of(grantRepository.save(grant));
    }

    /**
     * Find a matching active grant for this invocation.
     * HIGH risk never matches unless {@code archops.approval.allow-high-grants=true}.
     * {@code propose_architecture_update} never matches.
     */
    @Transactional(readOnly = true)
    public Optional<ExecutionGrant> findMatching(
            Long userId,
            Long conversationId,
            String toolName,
            Long assetId,
            RiskLevel risk,
            String commandOrArgs) {
        if (userId == null || conversationId == null || toolName == null || risk == null) {
            return Optional.empty();
        }
        if (PROPOSE_ARCHITECTURE_UPDATE.equals(toolName) || !isGrantableTool(toolName)) {
            return Optional.empty();
        }
        if (!canGrantRisk(risk)) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        var candidates = assetId == null
                ? grantRepository.findActiveConversationWideMatches(
                        userId, conversationId, toolName, risk, now)
                : grantRepository.findActiveMatches(
                        userId, conversationId, toolName, risk, assetId, now);

        String haystack = commandOrArgs != null ? commandOrArgs : "";
        return candidates.stream()
                .filter(g -> patternMatches(g.getPattern(), haystack))
                .findFirst();
    }

    private static boolean patternMatches(String pattern, String haystack) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        return haystack.startsWith(pattern) || haystack.contains("\"command\":\"" + pattern);
    }

    private PayloadFields parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new PayloadFields(null, null, null, null);
        }
        try {
            var map = objectMapper.readValue(payloadJson, new TypeReference<java.util.Map<String, Object>>() {});
            String tool = asString(map.get("tool"));
            Long conversationId = asLong(map.get("conversationId"));
            Long assetId = null;
            String pattern = null;
            Object argumentsRaw = map.get("arguments");
            if (argumentsRaw instanceof String argsJson) {
                try {
                    var args = objectMapper.readValue(argsJson, new TypeReference<java.util.Map<String, Object>>() {});
                    assetId = asLong(args.get("assetId"));
                    String command = asString(args.get("command"));
                    if (command != null && !command.isBlank()) {
                        pattern = command.trim();
                        // Keep pattern bounded; use first token as prefix for broader reuse
                        int space = pattern.indexOf(' ');
                        if (space > 0) {
                            pattern = pattern.substring(0, space);
                        }
                        if (pattern.length() > 512) {
                            pattern = pattern.substring(0, 512);
                        }
                    }
                } catch (Exception ignored) {
                    // arguments may not be JSON object
                }
            }
            return new PayloadFields(tool, conversationId, assetId, pattern);
        } catch (Exception ex) {
            log.warn("Failed to parse approval payload for grant: {}", ex.getMessage());
            return new PayloadFields(null, null, null, null);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record PayloadFields(String toolName, Long conversationId, Long assetId, String pattern) {}
}
