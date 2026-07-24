package com.archops.ai.service;

import com.archops.ai.context.AgentContextAssembler;
import com.archops.ai.domain.AiConversation;
import com.archops.ai.domain.AiMessage;
import com.archops.ai.dto.UiContext;
import com.archops.ai.llm.LlmProvider.ChatMessage;
import com.archops.ai.llm.LlmProvider.CompletionResult;
import com.archops.ai.llm.LlmProvider.ToolCall;
import com.archops.ai.llm.LlmProvider.ToolDefinition;
import com.archops.ai.runtime.LlmGenerationConfig;
import com.archops.ai.runtime.LlmRuntime;
import com.archops.ai.runtime.LlmRuntimeResolver;
import com.archops.ai.provider.domain.AiProvider;
import com.archops.ai.repository.AiMessageRepository;
import com.archops.approval.domain.Approval;
import com.archops.approval.domain.ApprovalStatus;
import com.archops.approval.service.ApprovalService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.service.ArchitectureProposalService;
import com.archops.knowledge.architecture.service.ToolExecutionEventService;
import com.archops.knowledge.classifier.ChangeClassifier;
import com.archops.knowledge.classifier.ChangeLevel;
import com.archops.knowledge.classifier.Classification;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.service.WorkLogWriter;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String PROPOSE_TOOL = "propose_architecture_update";

    private final LlmRuntimeResolver llmRuntimeResolver;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorService toolExecutorService;
    private final ConversationService conversationService;
    private final AgentContextAssembler agentContextAssembler;
    private final AiMessageRepository messageRepository;
    private final ApprovalService approvalService;
    private final ChangeClassifier changeClassifier;
    private final ToolExecutionEventService toolExecutionEventService;
    private final WorkLogWriter workLogWriter;
    private final ArchitectureProposalService proposalService;
    private final ObjectMapper objectMapper;

    public AiAgentService(
            LlmRuntimeResolver llmRuntimeResolver,
            ToolRegistry toolRegistry,
            ToolExecutorService toolExecutorService,
            ConversationService conversationService,
            AgentContextAssembler agentContextAssembler,
            AiMessageRepository messageRepository,
            ApprovalService approvalService,
            ChangeClassifier changeClassifier,
            ToolExecutionEventService toolExecutionEventService,
            WorkLogWriter workLogWriter,
            ArchitectureProposalService proposalService,
            ObjectMapper objectMapper) {
        this.llmRuntimeResolver = llmRuntimeResolver;
        this.toolRegistry = toolRegistry;
        this.toolExecutorService = toolExecutorService;
        this.conversationService = conversationService;
        this.agentContextAssembler = agentContextAssembler;
        this.messageRepository = messageRepository;
        this.approvalService = approvalService;
        this.changeClassifier = changeClassifier;
        this.toolExecutionEventService = toolExecutionEventService;
        this.workLogWriter = workLogWriter;
        this.proposalService = proposalService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentResult chat(
            Long userId,
            Long conversationId,
            String userMessage,
            Long providerId,
            Consumer<AgentEvent> onEvent) {
        return chat(userId, conversationId, userMessage, providerId, null, onEvent);
    }

    @Transactional
    public AgentResult chat(
            Long userId,
            Long conversationId,
            String userMessage,
            Long providerId,
            UiContext uiContext,
            Consumer<AgentEvent> onEvent) {
        AiConversation conversation = conversationService.requireOwned(conversationId, userId);
        conversationService.appendMessage(conversationId, "user", userMessage, "[]");
        if (onEvent != null) {
            onEvent.accept(AgentEvent.userMessage(userMessage));
        }

        LlmRuntimeResolver.ResolvedRuntime resolved;
        try {
            resolved = llmRuntimeResolver.resolve(providerId);
        } catch (Exception ex) {
            String err = ex.getMessage() != null ? ex.getMessage() : "未配置可用的 AI Provider，请在系统设置中添加";
            conversationService.appendMessage(conversationId, "assistant", err, "[]");
            if (onEvent != null) {
                onEvent.accept(AgentEvent.done(err));
            }
            return new AgentResult(err, List.of());
        }

        List<ChatMessage> messages =
                buildContext(conversation, conversationId, userMessage, uiContext, resolved.providerId());
        List<Long> effectiveTargets = conversationService.resolveEffectiveTargetAssetIds(conversation);
        AgentTool.ExecutionContext toolContext = new AgentTool.ExecutionContext(
                userId, null, conversationId, effectiveTargets, resolved.providerId());

        return runAgentLoop(
                resolved.runtime(),
                messages,
                userId,
                conversationId,
                conversation,
                resolved.providerId(),
                toolContext,
                new ArrayList<>(),
                onEvent);
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

        LlmRuntimeResolver.ResolvedRuntime resolved;
        try {
            resolved = llmRuntimeResolver.resolve(providerId);
        } catch (Exception ex) {
            String err = ex.getMessage() != null ? ex.getMessage() : "未配置可用的 AI Provider";
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LLM_UNAVAILABLE", err);
        }
        Long effectiveProviderId = resolved.providerId();
        toolContext = new AgentTool.ExecutionContext(
                userId, null, conversationId, effectiveTargets, effectiveProviderId);

        List<ChatMessage> messages = buildResumeContext(conversation, conversationId, null, effectiveProviderId);
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
        postProcessToolResult(
                toolCall, exec, userId, conversationId, conversation, toolSummaries, messages, onEvent, false);

        return runAgentLoop(
                resolved.runtime(),
                messages,
                userId,
                conversationId,
                conversation,
                effectiveProviderId,
                toolContext,
                toolSummaries,
                onEvent);
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

            boolean proposedInTurn = result.toolCalls().stream()
                    .anyMatch(tc -> PROPOSE_TOOL.equals(tc.name()));
            ChangeLevel highestLevel = ChangeLevel.L0;

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

                if ("SUCCESS".equals(exec.status()) || exec.status() == null || "OK".equals(exec.status())) {
                    ChangeLevel level = postProcessToolResult(
                            toolCall, exec, userId, conversationId, conversation, toolSummaries, messages, onEvent, true);
                    if (level.ordinal() > highestLevel.ordinal()) {
                        highestLevel = level;
                    }
                    if (PROPOSE_TOOL.equals(toolCall.name())) {
                        proposedInTurn = true;
                        emitProposalCreatedEvent(exec.output(), onEvent);
                    }
                }
            }

            if ((highestLevel == ChangeLevel.L1 || highestLevel == ChangeLevel.L2)
                    && !proposedInTurn) {
                String nudge = "System: Classification indicates "
                        + highestLevel
                        + ". Call propose_architecture_update with partitionKey/facts/evidence before concluding.";
                messages.add(ChatMessage.system(nudge));
            }

            if (highestLevel == ChangeLevel.L2 && !proposedInTurn) {
                autoCreateDraftProposal(userId, conversationId, conversation, toolSummaries, onEvent);
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

    private ChangeLevel postProcessToolResult(
            ToolCall toolCall,
            ToolExecutorService.ToolExecutionResult exec,
            Long userId,
            Long conversationId,
            AiConversation conversation,
            List<ToolExecutionSummary> toolSummaries,
            List<ChatMessage> messages,
            Consumer<AgentEvent> onEvent,
            boolean mayNudgeInline) {
        Classification classification = changeClassifier.classify(
                toolCall.name(), toolCall.arguments(), exec.output());
        List<Long> assetIds = extractAssetIds(toolCall.arguments(), conversation);
        List<Long> groupIds = conversation.getTargetGroupIds() != null
                ? new ArrayList<>(conversation.getTargetGroupIds())
                : List.of();

        try {
            toolExecutionEventService.record(
                    conversationId,
                    userId,
                    toolCall.name(),
                    toolCall.arguments(),
                    exec.output(),
                    null,
                    null,
                    assetIds,
                    classification.level());
        } catch (Exception ex) {
            log.warn("Failed to persist tool execution event: {}", ex.getMessage());
        }

        WorkLog workLog = null;
        try {
            workLog = workLogWriter.appendAgentToolLog(
                    conversationId,
                    userId,
                    toolCall.name(),
                    "[" + classification.level() + "] " + toolCall.name() + ": " + truncate(exec.output(), 500),
                    classification.level(),
                    assetIds,
                    groupIds,
                    "{\"tool\":\"" + toolCall.name() + "\",\"rationale\":"
                            + quote(classification.rationale()) + "}");
            if (onEvent != null && workLog != null) {
                onEvent.accept(AgentEvent.workLogAppended(
                        workLog.getId(), conversationId, classification.level().name()));
            }
        } catch (Exception ex) {
            log.warn("Failed to append work log: {}", ex.getMessage());
        }

        if (mayNudgeInline
                && (classification.level() == ChangeLevel.L1 || classification.level() == ChangeLevel.L2)
                && !PROPOSE_TOOL.equals(toolCall.name())) {
            messages.add(ChatMessage.system(
                    "System nudge (" + classification.level() + "): "
                            + classification.rationale()
                            + ". Call propose_architecture_update."));
        }

        return classification.level();
    }

    private void autoCreateDraftProposal(
            Long userId,
            Long conversationId,
            AiConversation conversation,
            List<ToolExecutionSummary> toolSummaries,
            Consumer<AgentEvent> onEvent) {
        try {
            List<Long> targets = conversationService.resolveEffectiveTargetAssetIds(conversation);
            String partitionKey = !targets.isEmpty()
                    ? PartitionKeys.asset(targets.getFirst())
                    : PartitionKeys.GLOBAL;
            String summary = "Auto-draft L2 proposal from agent turn (model did not call "
                    + PROPOSE_TOOL + ")";
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("conversationId", conversationId);
            evidence.put("tools", toolSummaries.stream().map(ToolExecutionSummary::tool).toList());
            String evidenceJson = objectMapper.writeValueAsString(List.of(evidence));

            ArchitectureProposal proposal = proposalService.createFromTool(
                    partitionKey,
                    summary,
                    List.of(),
                    evidenceJson,
                    userId,
                    conversationId,
                    ProposalStatus.DRAFT,
                    "HIGH",
                    0.3);
            if (onEvent != null) {
                onEvent.accept(AgentEvent.architectureProposalCreated(
                        proposal.getId(),
                        proposal.getPartitionKey(),
                        proposal.getSummary(),
                        proposal.getStatus().name()));
            }
        } catch (Exception ex) {
            log.warn("Failed to auto-create L2 draft proposal: {}", ex.getMessage());
        }
    }

    private void emitProposalCreatedEvent(String toolOutput, Consumer<AgentEvent> onEvent) {
        if (onEvent == null || toolOutput == null) {
            return;
        }
        Long proposalId = extractLongAfter(toolOutput, "id=");
        String partitionKey = extractAfter(toolOutput, "partitionKey=");
        String status = extractAfter(toolOutput, "status=");
        if (proposalId != null) {
            onEvent.accept(AgentEvent.architectureProposalCreated(
                    proposalId, partitionKey, toolOutput, status != null ? status : "PENDING_REVIEW"));
        }
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

    private List<ChatMessage> buildContext(
            AiConversation conversation,
            Long conversationId,
            String latestUserMessage,
            UiContext uiContext,
            Long providerId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(
                assembleSystemContext(conversation, conversationId, latestUserMessage, uiContext, providerId)));

        messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(m -> !"user".equals(m.getRole()) || !m.getContent().equals(latestUserMessage))
                .forEach(m -> messages.add(toChatMessage(m)));
        messages.add(ChatMessage.user(latestUserMessage));
        return messages;
    }

    private List<ChatMessage> buildResumeContext(
            AiConversation conversation, Long conversationId, UiContext uiContext, Long providerId) {
        List<ChatMessage> messages = new ArrayList<>();
        String lastUser = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(AiMessage::getContent)
                .reduce((first, second) -> second)
                .orElse("");
        messages.add(ChatMessage.system(
                assembleSystemContext(conversation, conversationId, lastUser, uiContext, providerId)));

        messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .forEach(m -> messages.add(toChatMessage(m)));
        return messages;
    }

    private String assembleSystemContext(
            AiConversation conversation,
            Long conversationId,
            String userQuery,
            UiContext uiContext,
            Long providerId) {
        List<Long> assetIds = conversationService.resolveEffectiveTargetAssetIds(conversation);
        List<Long> groupIds = conversation.getTargetGroupIds() != null
                ? conversation.getTargetGroupIds()
                : List.of();
        Integer contextBudget = null;
        try {
            AiProvider provider = llmRuntimeResolver.resolveProvider(providerId);
            int budget = LlmGenerationConfig.from(provider).contextCharBudget();
            if (budget > 0) {
                contextBudget = budget;
            }
        } catch (Exception ignored) {
            // Fall back to platform default context budget.
        }
        return agentContextAssembler.assemble(
                conversation.getUserId(),
                assetIds,
                groupIds,
                userQuery,
                conversationId,
                uiContext,
                contextBudget);
    }

    private List<Long> extractAssetIds(String argumentsJson, AiConversation conversation) {
        try {
            Map<String, Object> args = objectMapper.readValue(
                    argumentsJson != null ? argumentsJson : "{}", new TypeReference<>() {});
            Object raw = args.get("assetId");
            if (raw instanceof Number number) {
                return List.of(number.longValue());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return conversationService.resolveEffectiveTargetAssetIds(conversation);
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : "");
        } catch (Exception ex) {
            return "\"\"";
        }
    }

    private static Long extractLongAfter(String text, String marker) {
        String part = extractAfter(text, marker);
        if (part == null) {
            return null;
        }
        try {
            String digits = part.split("[^0-9]")[0];
            return Long.parseLong(digits);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String extractAfter(String text, String marker) {
        int idx = text.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String rest = text.substring(idx + marker.length()).trim();
        int end = rest.indexOf(' ');
        return end > 0 ? rest.substring(0, end) : rest;
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
            Long conversationId,
            Long proposalId) {

        public static AgentEvent userMessage(String content) {
            return new AgentEvent("user", content, null, null, null, null, null, null);
        }

        public static AgentEvent token(String content) {
            return new AgentEvent("token", content, null, null, null, null, null, null);
        }

        public static AgentEvent toolStart(String tool, String args) {
            return new AgentEvent("tool_start", args, tool, null, null, null, null, null);
        }

        public static AgentEvent toolResult(String tool, String status, String output) {
            return new AgentEvent("tool_result", output, tool, status, null, null, null, null);
        }

        public static AgentEvent approvalRequired(Long id, String risk, String msg) {
            return new AgentEvent("approval_required", msg, null, null, id, risk, null, null);
        }

        public static AgentEvent resumeStart(Long conversationId, Long approvalId) {
            return new AgentEvent("resume_start", null, null, null, approvalId, null, conversationId, null);
        }

        public static AgentEvent done(String content) {
            return new AgentEvent("done", content, null, null, null, null, null, null);
        }

        public static AgentEvent architectureProposalCreated(
                Long proposalId, String partitionKey, String summary, String status) {
            String content = "{\"proposalId\":"
                    + proposalId
                    + ",\"partitionKey\":\""
                    + (partitionKey != null ? partitionKey : "")
                    + "\",\"summary\":"
                    + jsonString(summary)
                    + ",\"status\":\""
                    + (status != null ? status : "")
                    + "\"}";
            return new AgentEvent(
                    "architecture_proposal_created", content, null, status, null, null, null, proposalId);
        }

        public static AgentEvent workLogAppended(Long workLogId, Long conversationId, String level) {
            String content = "{\"workLogId\":"
                    + workLogId
                    + ",\"conversationId\":"
                    + conversationId
                    + ",\"level\":\""
                    + (level != null ? level : "")
                    + "\"}";
            return new AgentEvent("work_log_appended", content, null, level, null, null, conversationId, null);
        }

        private static String jsonString(String value) {
            if (value == null) {
                return "\"\"";
            }
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
