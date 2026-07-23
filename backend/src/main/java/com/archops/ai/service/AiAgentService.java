package com.archops.ai.service;

import com.archops.ai.domain.AiConversation;
import com.archops.ai.domain.AiMessage;
import com.archops.ai.llm.LlmProvider.ChatMessage;
import com.archops.ai.llm.LlmProvider.CompletionResult;
import com.archops.ai.llm.LlmProvider.ToolCall;
import com.archops.ai.llm.LlmProvider.ToolDefinition;
import com.archops.ai.runtime.LlmRuntime;
import com.archops.ai.runtime.LlmRuntimeResolver;
import com.archops.ai.repository.AiMessageRepository;
import com.archops.approval.domain.Approval;
import com.archops.approval.domain.ApprovalStatus;
import com.archops.approval.service.ApprovalService;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.service.KnowledgeContextService;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReAct-style agent loop: call LLM -> execute tools -> feed results back -> repeat.
 * Max iterations prevents runaway tool chains in production.
 */
@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);
    private static final int MAX_ITERATIONS = 5;

    private static final String SYSTEM_PROMPT = """
            You are ArchOps AI, an expert SRE assistant for a cloud-native operations platform.
            You help operators inspect and manage Linux server clusters, Kubernetes, Docker,
            and big-data stacks (Spark, Kafka, MinIO, Prometheus).
            Always prefer safe read-only diagnostics first. When you need to run commands,
            use the provided tools. Respond in the same language the user writes in.
            """;

    private final LlmRuntimeResolver llmRuntimeResolver;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorService toolExecutorService;
    private final ConversationService conversationService;
    private final KnowledgeContextService knowledgeContextService;
    private final AiMessageRepository messageRepository;
    private final AssetService assetService;
    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;

    public AiAgentService(
            LlmRuntimeResolver llmRuntimeResolver,
            ToolRegistry toolRegistry,
            ToolExecutorService toolExecutorService,
            ConversationService conversationService,
            KnowledgeContextService knowledgeContextService,
            AiMessageRepository messageRepository,
            AssetService assetService,
            ApprovalService approvalService,
            ObjectMapper objectMapper) {
        this.llmRuntimeResolver = llmRuntimeResolver;
        this.toolRegistry = toolRegistry;
        this.toolExecutorService = toolExecutorService;
        this.conversationService = conversationService;
        this.knowledgeContextService = knowledgeContextService;
        this.messageRepository = messageRepository;
        this.assetService = assetService;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentResult chat(Long userId, Long conversationId, String userMessage, Long providerId, Consumer<AgentEvent> onEvent) {
        AiConversation conversation = conversationService.requireOwned(conversationId, userId);
        conversationService.appendMessage(conversationId, "user", userMessage, "[]");
        if (onEvent != null) {
            onEvent.accept(AgentEvent.userMessage(userMessage));
        }

        List<ChatMessage> messages = buildContext(conversation, conversationId, userMessage);
        List<Long> effectiveTargets = conversationService.resolveEffectiveTargetAssetIds(conversation);
        AgentTool.ExecutionContext toolContext = new AgentTool.ExecutionContext(
                userId, null, conversationId, effectiveTargets, providerId);

        LlmRuntime llm;
        try {
            llm = llmRuntimeResolver.resolve(providerId).runtime();
        } catch (Exception ex) {
            String err = ex.getMessage() != null ? ex.getMessage() : "未配置可用的 AI Provider，请在系统设置中添加";
            conversationService.appendMessage(conversationId, "assistant", err, "[]");
            if (onEvent != null) {
                onEvent.accept(AgentEvent.done(err));
            }
            return new AgentResult(err, List.of());
        }

        return runAgentLoop(llm, messages, userId, conversationId, conversation, providerId, toolContext, new ArrayList<>(), onEvent);
    }

    @Transactional
    public AgentResult resumeAfterApproval(Long approvalId, Consumer<AgentEvent> onEvent) {
        Approval approval = approvalService.getRequired(approvalId);
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "APPROVAL_NOT_APPROVED", "审批未通过，无法恢复 Agent");
        }

        Map<String, Object> payload = parseApprovalPayload(approval.getPayload());
        Long conversationId = asLong(payload.get("conversationId"));
        if (conversationId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_CONVERSATION", "审批载荷缺少 conversationId");
        }
        Long providerId = asLong(payload.get("providerId"));
        String toolName = String.valueOf(payload.get("tool"));
        String toolArgs = String.valueOf(payload.get("arguments"));
        Long userId = approval.getRequesterId();

        AiConversation conversation = conversationService.requireOwned(conversationId, userId);
        List<Long> effectiveTargets = conversationService.resolveEffectiveTargetAssetIds(conversation);
        AgentTool.ExecutionContext toolContext = new AgentTool.ExecutionContext(
                userId, null, conversationId, effectiveTargets, providerId);
        ToolCall toolCall = new ToolCall("approval-" + approvalId, toolName, toolArgs);

        LlmRuntime llm;
        try {
            llm = llmRuntimeResolver.resolve(providerId).runtime();
        } catch (Exception ex) {
            String err = ex.getMessage() != null ? ex.getMessage() : "未配置可用的 AI Provider";
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LLM_UNAVAILABLE", err);
        }

        List<ChatMessage> messages = buildResumeContext(conversation, conversationId);
        List<ToolExecutionSummary> toolSummaries = new ArrayList<>();

        if (onEvent != null) {
            onEvent.accept(AgentEvent.resumeStart(conversationId, approvalId));
        }
        if (onEvent != null) {
            onEvent.accept(AgentEvent.toolStart(toolCall.name(), toolCall.arguments()));
        }

        ToolExecutorService.ToolExecutionResult exec =
                toolExecutorService.execute(toolCall, userId, approvalId, toolContext);
        toolSummaries.add(new ToolExecutionSummary(toolCall.name(), exec.status(), exec.output()));

        if ("FAILED".equals(exec.status()) || "REJECTED".equals(exec.status())) {
            String err = exec.output();
            if (onEvent != null) {
                onEvent.accept(AgentEvent.toolResult(toolCall.name(), exec.status(), exec.output()));
            }
            conversationService.appendMessage(conversationId, "assistant", err, writeToolSummaries(toolSummaries));
            if (onEvent != null) {
                onEvent.accept(AgentEvent.done(err));
            }
            return new AgentResult(err, toolSummaries);
        }

        String toolResult = "[" + toolCall.name() + "] " + exec.output();
        if (onEvent != null) {
            onEvent.accept(AgentEvent.toolResult(toolCall.name(), exec.status(), exec.output()));
        }
        messages.add(ChatMessage.tool(toolResult));

        return runAgentLoop(llm, messages, userId, conversationId, conversation, providerId, toolContext, toolSummaries, onEvent);
    }

    private AgentResult runAgentLoop(
            LlmRuntime llm,
            List<ChatMessage> messages,
            Long userId,
            Long conversationId,
            AiConversation conversation,
            Long providerId,
            AgentTool.ExecutionContext toolContext,
            List<ToolExecutionSummary> toolSummaries,
            Consumer<AgentEvent> onEvent) {
        List<ToolDefinition> tools = toolRegistry.definitions();
        String finalAnswer = "";

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            CompletionResult result = completeLlm(llm, messages, tools, onEvent);

            if (result.toolCalls() == null || result.toolCalls().isEmpty()) {
                finalAnswer = result.content();
                break;
            }

            String assistantNote = result.content() != null ? result.content() : "";
            messages.add(new ChatMessage("assistant", assistantNote, result.toolCalls()));

            for (ToolCall toolCall : result.toolCalls()) {
                if (onEvent != null) {
                    onEvent.accept(AgentEvent.toolStart(toolCall.name(), toolCall.arguments()));
                }
                ToolExecutorService.ToolExecutionResult exec =
                        toolExecutorService.execute(toolCall, userId, null, toolContext);
                toolSummaries.add(new ToolExecutionSummary(toolCall.name(), exec.status(), exec.output()));

                if ("PENDING_APPROVAL".equals(exec.status())) {
                    conversationService.appendMessage(
                            conversationId,
                            "assistant",
                            assistantNote,
                            writeToolCalls(result.toolCalls()));
                    String msg = "操作需要人工审批 (approvalId=" + exec.approvalId() + ", risk="
                            + exec.riskLevel() + "): " + toolCall.name();
                    if (onEvent != null) {
                        onEvent.accept(AgentEvent.approvalRequired(exec.approvalId(), exec.riskLevel().name(), msg));
                        onEvent.accept(AgentEvent.done(msg));
                    }
                    return new AgentResult(msg, toolSummaries);
                }

                String toolResult = "[" + toolCall.name() + "] " + exec.output();
                if (onEvent != null) {
                    onEvent.accept(AgentEvent.toolResult(toolCall.name(), exec.status(), exec.output()));
                }
                messages.add(ChatMessage.tool(toolResult));
            }
        }

        if (finalAnswer.isBlank()) {
            finalAnswer = "已达到最大工具调用轮次，请缩小问题范围后重试。";
        }

        conversationService.appendMessage(conversationId, "assistant", finalAnswer, writeToolSummaries(toolSummaries));
        if (onEvent != null) {
            onEvent.accept(AgentEvent.done(finalAnswer));
        }
        return new AgentResult(finalAnswer, toolSummaries);
    }

    private CompletionResult completeLlm(
            LlmRuntime llm,
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<AgentEvent> onEvent) {
        if (onEvent == null) {
            return llm.complete(messages, tools);
        }
        return llm.streamComplete(messages, tools, token -> onEvent.accept(AgentEvent.token(token)));
    }

    private List<ChatMessage> buildContext(AiConversation conversation, Long conversationId, String latestUserMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        String knowledge = knowledgeContextService.buildContextSnippet(latestUserMessage);
        String targets = formatTargetAssets(conversationService.resolveEffectiveTargetAssetIds(conversation));
        messages.add(ChatMessage.system(SYSTEM_PROMPT + "\n\n" + targets + "\n\n" + knowledge));

        messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(m -> !"user".equals(m.getRole()) || !m.getContent().equals(latestUserMessage))
                .forEach(m -> messages.add(toChatMessage(m)));
        messages.add(ChatMessage.user(latestUserMessage));
        return messages;
    }

    private List<ChatMessage> buildResumeContext(AiConversation conversation, Long conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        String lastUser = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(AiMessage::getContent)
                .reduce((first, second) -> second)
                .orElse("");
        String knowledge = knowledgeContextService.buildContextSnippet(lastUser);
        String targets = formatTargetAssets(conversationService.resolveEffectiveTargetAssetIds(conversation));
        messages.add(ChatMessage.system(SYSTEM_PROMPT + "\n\n" + targets + "\n\n" + knowledge));

        messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .forEach(m -> messages.add(toChatMessage(m)));
        return messages;
    }

    private ChatMessage toChatMessage(AiMessage message) {
        return new ChatMessage(message.getRole(), message.getContent(), readToolCalls(message.getToolCalls()));
    }

    private List<ToolCall> readToolCalls(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ToolCall>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse tool calls JSON", ex);
            return List.of();
        }
    }

    private Map<String, Object> parseApprovalPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_PAYLOAD", "审批载荷无效");
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String formatTargetAssets(List<Long> targetAssetIds) {
        if (targetAssetIds == null || targetAssetIds.isEmpty()) {
            return "Active target assets: none. Ask the user to select target assets before running ssh_exec, "
                    + "or pass assetId explicitly after listing assets.";
        }
        StringBuilder sb = new StringBuilder(
                "Active target assets. When ssh_exec omits assetId, the command runs on ALL targets sequentially:\n");
        for (Long assetId : targetAssetIds) {
            try {
                AssetResponse asset = assetService.get(assetId);
                sb.append("- id=").append(asset.id())
                        .append(" name=").append(asset.name())
                        .append(" host=").append(asset.host() != null ? asset.host() : "n/a")
                        .append('\n');
            } catch (Exception ex) {
                sb.append("- id=").append(assetId).append(" (unavailable)\n");
            }
        }
        return sb.toString();
    }

    private String writeToolSummaries(List<ToolExecutionSummary> summaries) {
        try {
            return objectMapper.writeValueAsString(summaries);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String writeToolCalls(List<ToolCall> toolCalls) {
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public record AgentResult(String answer, List<ToolExecutionSummary> tools) {}

    public record ToolExecutionSummary(String tool, String status, String output) {}

    public record AgentEvent(
            String type,
            String content,
            String tool,
            String status,
            Long approvalId,
            String risk,
            Long conversationId) {

        public static AgentEvent userMessage(String content) {
            return new AgentEvent("user", content, null, null, null, null, null);
        }

        public static AgentEvent token(String content) {
            return new AgentEvent("token", content, null, null, null, null, null);
        }

        public static AgentEvent toolStart(String tool, String args) {
            return new AgentEvent("tool_start", args, tool, null, null, null, null);
        }

        public static AgentEvent toolResult(String tool, String status, String output) {
            return new AgentEvent("tool_result", output, tool, status, null, null, null);
        }

        public static AgentEvent approvalRequired(Long id, String risk, String msg) {
            return new AgentEvent("approval_required", msg, null, null, id, risk, null);
        }

        public static AgentEvent resumeStart(Long conversationId, Long approvalId) {
            return new AgentEvent("resume_start", null, null, null, approvalId, null, conversationId);
        }

        public static AgentEvent done(String content) {
            return new AgentEvent("done", content, null, null, null, null, null);
        }
    }
}
