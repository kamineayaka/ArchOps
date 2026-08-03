package com.archops.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.ai.llm.LlmProvider.ChatMessage;
import com.archops.ai.llm.LlmProvider.ToolCall;
import com.archops.ai.llm.LlmProvider.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicRuntimeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicRuntime runtime =
            new AnthropicRuntime("https://example.test", "key", "claude-test", 5_000, mapper);

    @Test
    void serializesToolUseBlocksAndCorrelatedToolResults() throws Exception {
        ToolCall call = new ToolCall("toolu_123", "ssh_exec", "{\"command\":\"df -h\"}");
        List<ChatMessage> messages = List.of(
                ChatMessage.system("sys"),
                ChatMessage.user("check disk"),
                new ChatMessage("assistant", "Running df", List.of(call)),
                ChatMessage.tool("toolu_123", "[ssh_exec] / 20%"));

        String body = runtime.buildRequestBody(messages, List.of(
                new ToolDefinition(
                        "ssh_exec",
                        "run ssh",
                        "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}}}")), false);

        JsonNode root = mapper.readTree(body);
        assertEquals("sys", root.path("system").asText());
        JsonNode msgs = root.path("messages");
        assertEquals(3, msgs.size());

        JsonNode assistant = msgs.get(1);
        assertEquals("assistant", assistant.path("role").asText());
        assertEquals("text", assistant.path("content").get(0).path("type").asText());
        assertEquals("tool_use", assistant.path("content").get(1).path("type").asText());
        assertEquals("toolu_123", assistant.path("content").get(1).path("id").asText());
        assertEquals("ssh_exec", assistant.path("content").get(1).path("name").asText());
        assertEquals("df -h", assistant.path("content").get(1).path("input").path("command").asText());

        JsonNode toolResult = msgs.get(2);
        assertEquals("user", toolResult.path("role").asText());
        assertEquals("tool_result", toolResult.path("content").get(0).path("type").asText());
        assertEquals("toolu_123", toolResult.path("content").get(0).path("tool_use_id").asText());
        assertTrue(toolResult.path("content").get(0).path("content").asText().contains("ssh_exec"));
    }
}
