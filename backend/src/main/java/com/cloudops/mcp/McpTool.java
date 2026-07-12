package com.cloudops.mcp;

import com.cloudops.ai.llm.LlmProvider.ToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * A single executable tool exposed to the AI agent. Tools are registered with
 * the {@link ToolRegistry} and discovered by the agent at conversation time.
 */
public interface McpTool {

    String name();

    String description();

    /** JSON schema describing the parameters the model must supply. */
    String parametersJson();

    /**
     * Execute the tool with the arguments produced by the model. The returned
     * string is fed back into the conversation as a {@code tool} role message.
     */
    String execute(Map<String, Object> arguments, ExecutionContext context) throws Exception;

    record ExecutionContext(
            Long userId,
            String username,
            Long conversationId,
            List<Long> targetAssetIds) {

        public ExecutionContext(Long userId, String username) {
            this(userId, username, null, List.of());
        }
    }
}
