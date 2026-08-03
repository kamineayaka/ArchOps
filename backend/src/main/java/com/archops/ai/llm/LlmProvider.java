package com.archops.ai.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared LLM message / tool contracts used by {@link com.archops.ai.runtime.LlmRuntime}.
 * Active provider selection is database-driven via {@link com.archops.ai.runtime.LlmRuntimeResolver}.
 */
public interface LlmProvider {

    String name();

    /**
     * Synchronous completion with optional tool definitions. The returned
     * {@link CompletionResult} may carry either a textual answer or a list of
     * tool-call requests that the agent must execute and feed back.
     */
    CompletionResult complete(List<ChatMessage> messages, List<ToolDefinition> tools);

    /**
     * Streaming completion. Tokens are delivered to {@code onToken} as they
     * arrive. When the model requests tool calls, they arrive via
     * {@link CompletionResult#toolCalls()} after the stream completes.
     */
    void streamComplete(List<ChatMessage> messages, List<ToolDefinition> tools, Consumer<String> onToken);

    /**
     * @param toolCallId required for {@code role=tool} so OpenAI/Anthropic can correlate
     *                   the result with the assistant's prior tool request
     */
    record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
        public ChatMessage(String role, String content, List<ToolCall> toolCalls) {
            this(role, content, toolCalls, null);
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, List.of(), null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, List.of(), null);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content, List.of(), null);
        }

        /** @deprecated prefer {@link #tool(String, String)} with the model tool-call id */
        @Deprecated
        public static ChatMessage tool(String content) {
            return new ChatMessage("tool", content, List.of(), null);
        }

        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, List.of(), toolCallId);
        }
    }

    record ToolDefinition(String name, String description, String parametersJson) {}

    record ToolCall(String id, String name, String arguments) {}

    record CompletionResult(String content, List<ToolCall> toolCalls) {}
}
