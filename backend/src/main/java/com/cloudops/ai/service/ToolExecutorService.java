package com.cloudops.ai.service;

import com.cloudops.approval.domain.Approval;
import com.cloudops.approval.domain.ApprovalStatus;
import com.cloudops.approval.domain.RiskLevel;
import com.cloudops.approval.service.ApprovalGate;
import com.cloudops.approval.service.ApprovalService;
import com.cloudops.approval.service.RiskClassifier;
import com.cloudops.ai.llm.LlmProvider.ToolCall;
import com.cloudops.audit.service.AuditService;
import com.cloudops.common.exception.BusinessException;
import com.cloudops.mcp.McpTool;
import com.cloudops.mcp.ToolRegistry;
import com.cloudops.user.domain.User;
import com.cloudops.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolExecutorService {

    private final ToolRegistry toolRegistry;
    private final RiskClassifier riskClassifier;
    private final ApprovalGate approvalGate;
    private final ApprovalService approvalService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ToolExecutorService(
            ToolRegistry toolRegistry,
            RiskClassifier riskClassifier,
            ApprovalGate approvalGate,
            ApprovalService approvalService,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.riskClassifier = riskClassifier;
        this.approvalGate = approvalGate;
        this.approvalService = approvalService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ToolExecutionResult execute(ToolCall toolCall, Long userId, Long approvalId) {
        return execute(toolCall, userId, approvalId, new McpTool.ExecutionContext(userId, null));
    }

    @Transactional
    public ToolExecutionResult execute(
            ToolCall toolCall,
            Long userId,
            Long approvalId,
            McpTool.ExecutionContext toolContext) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        McpTool tool = toolRegistry.find(toolCall.name())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "TOOL_NOT_FOUND", "工具不存在: " + toolCall.name()));

        RiskLevel risk = riskClassifier.classify(toolCall.name(), toolCall.arguments());
        ApprovalGate.Decision decision = approvalGate.decide(user.getRbacTier(), user.getApprovalPolicy(), risk);

        if (!decision.autoExecute()) {
            if (approvalId == null) {
                Approval pending = approvalService.createPending(
                        userId,
                        "tool:" + toolCall.name(),
                        toolCall.name(),
                        risk,
                        Map.of("tool", toolCall.name(), "arguments", toolCall.arguments()));
                return ToolExecutionResult.pendingApproval(pending.getId(), risk, toolCall);
            }
            Approval approval = approvalService.getRequired(approvalId);
            if (approval.getStatus() != ApprovalStatus.APPROVED) {
                return ToolExecutionResult.rejected("审批未通过，工具未执行");
            }
        }

        try {
            Map<String, Object> args = objectMapper.readValue(
                    toolCall.arguments(), new TypeReference<Map<String, Object>>() {});
            McpTool.ExecutionContext context = new McpTool.ExecutionContext(
                    userId,
                    user.getUsername(),
                    toolContext.conversationId(),
                    toolContext.targetAssetIds());
            String output = tool.execute(args, context);
            auditService.record(new AuditService.AuditEntry(
                    userId,
                    user.getUsername(),
                    "tool.execute",
                    toolCall.name(),
                    risk.name(),
                    "SUCCESS",
                    toolCall.arguments(),
                    null,
                    null));
            return ToolExecutionResult.success(output);
        } catch (Exception ex) {
            auditService.record(new AuditService.AuditEntry(
                    userId,
                    user.getUsername(),
                    "tool.execute",
                    toolCall.name(),
                    risk.name(),
                    "FAILED",
                    ex.getMessage(),
                    null,
                    null));
            return ToolExecutionResult.failed(ex.getMessage());
        }
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
