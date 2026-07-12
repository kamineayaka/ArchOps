package com.cloudops.ai.service;

import com.cloudops.ai.domain.AiConversation;
import com.cloudops.ai.llm.LlmProvider.ChatMessage;
import com.cloudops.ai.llm.LlmProvider.CompletionResult;
import com.cloudops.ai.llm.LlmProvider.ToolCall;
import com.cloudops.ai.runtime.LlmRuntime;
import com.cloudops.ai.runtime.LlmRuntimeResolver;
import com.cloudops.ai.repository.AiMessageRepository;
import com.cloudops.knowledge.service.KnowledgeContextService;
import com.cloudops.asset.dto.AssetResponse;
import com.cloudops.asset.service.AssetService;
import com.cloudops.mcp.McpTool;
import com.cloudops.mcp.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            You are CloudOps AI, an expert SRE assistant for a cloud-native operations platform.
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
    private final ObjectMapper objectMapper;

    public AiAgentService(
            LlmRuntimeResolver llmRuntimeResolver,
            ToolRegistry toolRegistry,
            ToolExecutorService toolExecutorService,
            ConversationService conversationService,
            KnowledgeContextService knowledgeContextService,
            AiMessageRepository messageRepository,
            AssetService assetService,
            ObjectMapper objectMapper) {
        this.llmRuntimeResolver = llmRuntimeResolver;
        this.toolRegistry = toolRegistry;
        this.toolExecutorService = toolExecutorService;
        this.conversationService = conversationService;
        this.knowledgeContextService = knowledgeContextService;
        this.messageRepository = messageRepository;
        this.assetService = assetService;
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
        McpTool.ExecutionContext toolContext = new McpTool.ExecutionContext(
                userId,
                null,
                conversationId,
                conversation.getTargetAssetIds());
        LlmRuntime llm;
        try {
            llm = llmRuntimeResolver.resolve(providerId).runtime();
        } catch (Exception ex) {
            String err = ex.getMessage() != null ? ex.getMessage() : "未配置可用的 AI Provider，请在系统设置中添加";
            conversationService.appendMessage(conversationId, "assistant", err, "[]");
            return new AgentResult(err, List.of());
        }

        List<ToolExecutionSummary> toolSummaries = new ArrayList<>();
        String finalAnswer = "";

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            CompletionResult result = llm.complete(messages, toolRegistry.definitions());

            if (result.toolCalls() == null || result.toolCalls().isEmpty()) {
                finalAnswer = result.content();
                if (onEvent != null && finalAnswer != null && !finalAnswer.isBlank()) {
                    onEvent.accept(AgentEvent.token(finalAnswer));
                }
                break;
            }

            String assistantNote = result.content() != null ? result.content() : "";
            if (!assistantNote.isBlank() && onEvent != null) {
                onEvent.accept(AgentEvent.token(assistantNote));
            }
            messages.add(new ChatMessage("assistant", assistantNote, result.toolCalls()));

            for (ToolCall toolCall : result.toolCalls()) {
                if (onEvent != null) {
                    onEvent.accept(AgentEvent.toolStart(toolCall.name(), toolCall.arguments()));
                }
                ToolExecutorService.ToolExecutionResult exec =
                        toolExecutorService.execute(toolCall, userId, null, toolContext);
                toolSummaries.add(new ToolExecutionSummary(toolCall.name(), exec.status(), exec.output()));

                if ("PENDING_APPROVAL".equals(exec.status())) {
                    String msg = "操作需要人工审批 (approvalId=" + exec.approvalId() + ", risk="
                            + exec.riskLevel() + "): " + toolCall.name();
                    if (onEvent != null) {
                        onEvent.accept(AgentEvent.approvalRequired(exec.approvalId(), exec.riskLevel().name(), msg));
                    }
                    messages.add(ChatMessage.tool(msg));
                    finalAnswer = msg;
                    break;
                }

                String toolResult = "[" + toolCall.name() + "] " + exec.output();
                if (onEvent != null) {
                    onEvent.accept(AgentEvent.toolResult(toolCall.name(), exec.status(), exec.output()));
                }
                messages.add(ChatMessage.tool(toolResult));
            }

            if (!finalAnswer.isBlank()) {
                break;
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

    private List<ChatMessage> buildContext(AiConversation conversation, Long conversationId, String latestUserMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        String knowledge = knowledgeContextService.buildContextSnippet(latestUserMessage);
        String targets = formatTargetAssets(conversation.getTargetAssetIds());
        messages.add(ChatMessage.system(SYSTEM_PROMPT + "\n\n" + targets + "\n\n" + knowledge));

        messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(m -> !"user".equals(m.getRole()) || !m.getContent().equals(latestUserMessage))
                .forEach(m -> messages.add(new ChatMessage(m.getRole(), m.getContent(), List.of())));
        messages.add(ChatMessage.user(latestUserMessage));
        return messages;
    }

    private String formatTargetAssets(List<Long> targetAssetIds) {
        if (targetAssetIds == null || targetAssetIds.isEmpty()) {
            return "Active target assets: none. Ask the user to select target assets before running ssh_exec, "
                    + "or pass assetId explicitly after listing assets.";
        }
        StringBuilder sb = new StringBuilder("Active target assets (prefer these for ssh_exec without assetId):\n");
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

    public record AgentResult(String answer, List<ToolExecutionSummary> tools) {}

    public record ToolExecutionSummary(String tool, String status, String output) {}

    public record AgentEvent(String type, String content, String tool, String status, Long approvalId, String risk) {
        public static AgentEvent userMessage(String content) { return new AgentEvent("user", content, null, null, null, null); }
        public static AgentEvent token(String content) { return new AgentEvent("token", content, null, null, null, null); }
        public static AgentEvent toolStart(String tool, String args) { return new AgentEvent("tool_start", args, tool, null, null, null); }
        public static AgentEvent toolResult(String tool, String status, String output) { return new AgentEvent("tool_result", output, tool, status, null, null); }
        public static AgentEvent approvalRequired(Long id, String risk, String msg) { return new AgentEvent("approval_required", msg, null, null, id, risk); }
        public static AgentEvent done(String content) { return new AgentEvent("done", content, null, null, null, null); }
    }
}
