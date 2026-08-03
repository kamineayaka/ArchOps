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

class OpenAiCompatRuntimeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiCompatRuntime runtime =
            new OpenAiCompatRuntime("https://example.test/v1", "key", "gpt-test", 5_000, mapper);

    @Test
    void serializesAssistantToolCallsAndToolResults() throws Exception {
        ToolCall call = new ToolCall("call_abc", "list_assets", "{\"limit\":5}");
        List<ChatMessage> messages = List.of(
                ChatMessage.system("sys"),
                ChatMessage.user("list hosts"),
                new ChatMessage("assistant", "", List.of(call)),
                ChatMessage.tool("call_abc", "[list_assets] ok"));

        String body = runtime.buildRequestBody(messages, List.of(
                new ToolDefinition("list_assets", "list", "{\"type\":\"object\",\"properties\":{}}")), false);

        JsonNode root = mapper.readTree(body);
        JsonNode msgs = root.path("messages");
        assertEquals(4, msgs.size());

        JsonNode assistant = msgs.get(2);
        assertEquals("assistant", assistant.path("role").asText());
        assertTrue(assistant.path("content").isNull());
        assertEquals("call_abc", assistant.path("tool_calls").get(0).path("id").asText());
        assertEquals("function", assistant.path("tool_calls").get(0).path("type").asText());
        assertEquals("list_assets", assistant.path("tool_calls").get(0).path("function").path("name").asText());

        JsonNode tool = msgs.get(3);
        assertEquals("tool", tool.path("role").asText());
        assertEquals("call_abc", tool.path("tool_call_id").asText());
        assertTrue(tool.path("content").asText().contains("list_assets"));
    }
}
