package com.archops.ai.service;

import com.archops.approval.domain.Approval;
import com.archops.approval.domain.ApprovalStatus;
import com.archops.approval.domain.DecisionSource;
import com.archops.approval.domain.ExecutionGrant;
import com.archops.approval.domain.RiskLevel;
import com.archops.approval.service.ApprovalGate;
import com.archops.approval.service.ApprovalService;
import com.archops.approval.service.ExecutionGrantService;
import com.archops.approval.service.RiskClassifier;
import com.archops.ai.llm.LlmProvider.ToolCall;
import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolRegistry;
import com.archops.user.domain.User;
import com.archops.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolExecutorService {

    private final ToolRegistry toolRegistry;
    private final RiskClassifier riskClassifier;
    private final ApprovalGate approvalGate;
    private final ApprovalService approvalService;
    private final ExecutionGrantService executionGrantService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ToolExecutorService(
            ToolRegistry toolRegistry,
            RiskClassifier riskClassifier,
            ApprovalGate approvalGate,
            ApprovalService approvalService,
            ExecutionGrantService executionGrantService,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.riskClassifier = riskClassifier;
        this.approvalGate = approvalGate;
        this.approvalService = approvalService;
        this.executionGrantService = executionGrantService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ToolExecutionResult execute(ToolCall toolCall, Long userId, Long approvalId) {
        return execute(toolCall, userId, approvalId, new AgentTool.ExecutionContext(userId, null));
    }

    @Transactional
    public ToolExecutionResult execute(
            ToolCall toolCall,
            Long userId,
            Long approvalId,
            AgentTool.ExecutionContext toolContext) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        AgentTool tool = toolRegistry.find(toolCall.name())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "TOOL_NOT_FOUND", "工具不存在: " + toolCall.name()));

        RiskLevel risk = riskClassifier.classify(toolCall.name(), toolCall.arguments());
        Long assetId = extractAssetId(toolCall.arguments());
        Long conversationId = toolContext.conversationId();

        // Session grant check before approval gate (EXECUTION tools only)
        if (approvalId == null && conversationId != null) {
            Optional<ExecutionGrant> grant = executionGrantService.findMatching(
                    userId, conversationId, toolCall.name(), assetId, risk, toolCall.arguments());
            if (grant.isPresent()) {
                return runTool(tool, toolCall, user, toolContext, risk, DecisionSource.GRANT);
            }
        }

        ApprovalGate.Decision decision = approvalGate.decide(user.getRbacTier(), user.getApprovalPolicy(), risk);

        if (!decision.autoExecute()) {
            if (approvalId == null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("tool", toolCall.name());
                payload.put("arguments", toolCall.arguments());
                if (toolCall.id() != null && !toolCall.id().isBlank()) {
                    payload.put("toolCallId", toolCall.id());
                }
                if (conversationId != null) {
                    payload.put("conversationId", conversationId);
                }
                if (toolContext.providerId() != null) {
                    payload.put("providerId", toolContext.providerId());
                }
                Approval pending = approvalService.createPending(
                        userId,
                        "tool:" + toolCall.name(),
                        toolCall.name(),
                        risk,
                        payload);
                return ToolExecutionResult.pendingApproval(pending.getId(), risk, toolCall);
            }
            Approval approval = approvalService.getRequired(approvalId);
            if (approval.getStatus() != ApprovalStatus.APPROVED) {
                auditTool(user, toolCall, risk, "DENIED", DecisionSource.DENY, "审批未通过，工具未执行");
                return ToolExecutionResult.rejected("审批未通过，工具未执行");
            }
            return runTool(tool, toolCall, user, toolContext, risk, DecisionSource.USER_APPROVAL);
        }

        return runTool(tool, toolCall, user, toolContext, risk, DecisionSource.AUTO_POLICY);
    }

    private ToolExecutionResult runTool(
            AgentTool tool,
            ToolCall toolCall,
            User user,
            AgentTool.ExecutionContext toolContext,
            RiskLevel risk,
            DecisionSource decisionSource) {
        try {
            Map<String, Object> args = objectMapper.readValue(
                    toolCall.arguments(), new TypeReference<Map<String, Object>>() {});
            AgentTool.ExecutionContext context = new AgentTool.ExecutionContext(
                    user.getId(),
                    user.getUsername(),
                    toolContext.conversationId(),
                    toolContext.targetAssetIds(),
                    toolContext.providerId(),
                    user.getRoles().stream().map(role -> role.getName()).toList());
            String output = tool.execute(args, context);
            auditTool(user, toolCall, risk, "SUCCESS", decisionSource, toolCall.arguments());
            return ToolExecutionResult.success(output);
        } catch (Exception ex) {
            auditTool(user, toolCall, risk, "FAILED", decisionSource, ex.getMessage());
            return ToolExecutionResult.failed(ex.getMessage());
        }
    }

    private void auditTool(
            User user,
            ToolCall toolCall,
            RiskLevel risk,
            String status,
            DecisionSource decisionSource,
            String detailPayload) {
        auditService.record(new AuditService.AuditEntry(
                user.getId(),
                user.getUsername(),
                "tool.execute",
                toolCall.name(),
                risk.name(),
                status,
                buildDetail(decisionSource, detailPayload),
                null,
                null));
    }

    private String buildDetail(DecisionSource decisionSource, String payload) {
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("decision_source", decisionSource.name());
            if (payload != null) {
                detail.put("payload", payload);
            }
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            return "{\"decision_source\":\"" + decisionSource.name() + "\"}";
        }
    }

    private Long extractAssetId(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> args = objectMapper.readValue(
                    argumentsJson, new TypeReference<Map<String, Object>>() {});
            Object raw = args.get("assetId");
            if (raw instanceof Number number) {
                return number.longValue();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    public record ToolExecutionResult(
            String status,
            String output,
            Long approvalId,
            RiskLevel riskLevel,
            ToolCall toolCall) {

        public static ToolExecutionResult success(String output) {
            return new ToolExecutionResult("SUCCESS", output, null, null, null);
        }

        public static ToolExecutionResult failed(String message) {
            return new ToolExecutionResult("FAILED", message, null, null, null);
        }

        public static ToolExecutionResult rejected(String message) {
            return new ToolExecutionResult("REJECTED", message, null, null, null);
        }

        public static ToolExecutionResult pendingApproval(Long approvalId, RiskLevel risk, ToolCall toolCall) {
            return new ToolExecutionResult("PENDING_APPROVAL", "等待人工审批", approvalId, risk, toolCall);
        }
    }
}
