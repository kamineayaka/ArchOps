package com.archops.tools;

import java.util.List;
import java.util.Map;

/**
 * A single executable tool exposed to the AI agent. Tools are registered with
 * the {@link ToolRegistry} and discovered by the agent at conversation time.
 *
 * <p>This is an in-process tool contract, not an MCP protocol implementation.
 */
public interface AgentTool {

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
            List<Long> targetAssetIds,
            Long providerId,
            List<String> roles) {

        public ExecutionContext {
            targetAssetIds = targetAssetIds != null ? List.copyOf(targetAssetIds) : List.of();
            roles = roles != null ? List.copyOf(roles) : List.of();
        }

        public ExecutionContext(Long userId, String username) {
            this(userId, username, null, List.of(), null, List.of());
        }

        public ExecutionContext(Long userId, String username, List<String> roles) {
            this(userId, username, null, List.of(), null, roles);
        }

        public ExecutionContext(Long userId, String username, Long conversationId, List<Long> targetAssetIds) {
            this(userId, username, conversationId, targetAssetIds, null, List.of());
        }

        public ExecutionContext(
                Long userId,
                String username,
                Long conversationId,
                List<Long> targetAssetIds,
                Long providerId) {
            this(userId, username, conversationId, targetAssetIds, providerId, List.of());
        }
    }
}
