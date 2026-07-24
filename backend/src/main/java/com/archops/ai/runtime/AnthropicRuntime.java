package com.archops.ai.runtime;

import com.archops.ai.llm.LlmProvider.ChatMessage;
import com.archops.ai.llm.LlmProvider.CompletionResult;
import com.archops.ai.llm.LlmProvider.ToolCall;
import com.archops.ai.llm.LlmProvider.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AnthropicRuntime implements LlmRuntime {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final List<String> KNOWN_MODELS = List.of(
            "claude-sonnet-4-20250514",
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229");

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;
    private final LlmGenerationConfig generation;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AnthropicRuntime(
            String baseUrl,
            String apiKey,
            String model,
            long timeoutMs,
            LlmGenerationConfig generation,
            ObjectMapper objectMapper) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.generation = generation != null
                ? generation
                : new LlmGenerationConfig(0, 0, false, com.archops.ai.provider.domain.ReasoningEffort.NONE);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public AnthropicRuntime(String baseUrl, String apiKey, String model, long timeoutMs, ObjectMapper objectMapper) {
        this(baseUrl, apiKey, model, timeoutMs, null, objectMapper);
    }

    @Override
    public CompletionResult complete(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            String body = buildRequestBody(messages, tools, false);
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                            .header("Content-Type", "application/json")
                            .header("x-api-key", apiKey)
                            .header("anthropic-version", ANTHROPIC_VERSION)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return new CompletionResult("[Anthropic error] " + response.statusCode(), List.of());
            }
            return parseResponse(response.body());
        } catch (Exception ex) {
            return new CompletionResult("[Anthropic request failed] " + ex.getMessage(), List.of());
        }
    }

    @Override
    public CompletionResult streamComplete(List<ChatMessage> messages, List<ToolDefinition> tools, Consumer<String> onToken) {
        StringBuilder content = new StringBuilder();
        try {
            String body = buildRequestBody(messages, tools, true);
            httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                            .header("Content-Type", "application/json")
                            .header("x-api-key", apiKey)
                            .header("anthropic-version", ANTHROPIC_VERSION)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofLines())
                    .body()
                    .forEach(line -> {
                        if (!line.startsWith("data: ")) {
                            return;
                        }
                        try {
                            JsonNode event = objectMapper.readTree(line.substring(6));
                            if ("content_block_delta".equals(event.path("type").asText())) {
                                String text = event.path("delta").path("text").asText("");
                                if (!text.isBlank()) {
                                    content.append(text);
                                    onToken.accept(text);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
            if (!content.isEmpty()) {
                return new CompletionResult(content.toString(), List.of());
            }
        } catch (Exception ex) {
            String err = "[stream failed] " + ex.getMessage();
            onToken.accept(err);
            return new CompletionResult(err, List.of());
        }
        CompletionResult result = complete(messages, tools);
        if (result.content() != null && !result.content().isBlank()) {
            onToken.accept(result.content());
        }
        return result;
    }

    public List<String> listModels() {
        return KNOWN_MODELS;
    }

    private String buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools, boolean stream) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", generation.effectiveMaxTokens(4096));
        root.put("stream", stream);
        if (generation.reasoningEnabled()) {
            int budget = generation.reasoningEffort().anthropicBudgetTokens();
            if (budget > 0) {
                ObjectNode thinking = root.putObject("thinking");
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", budget);
            }
        }

        String system = "";
        ArrayNode anthropicMessages = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.role())) {
                system = msg.content();
                continue;
            }
            if ("tool".equals(msg.role())) {
                ObjectNode toolResult = objectMapper.createObjectNode();
                toolResult.put("role", "user");
                ArrayNode content = toolResult.putArray("content");
                ObjectNode block = content.addObject();
                block.put("type", "tool_result");
                block.put("tool_use_id", "tool");
                block.put("content", msg.content());
                anthropicMessages.add(toolResult);
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", "assistant".equals(msg.role()) ? "assistant" : "user");
            node.put("content", msg.content());
            anthropicMessages.add(node);
        }
        if (!system.isBlank()) {
            root.put("system", system);
        }
        root.set("messages", anthropicMessages);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode t = toolsNode.addObject();
                t.put("name", tool.name());
                t.put("description", tool.description());
                t.set("input_schema", objectMapper.readTree(tool.parametersJson()));
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    private CompletionResult parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode contentBlocks = root.path("content");
        if (contentBlocks.isArray()) {
            for (JsonNode block : contentBlocks) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    content.append(block.path("text").asText(""));
                } else if ("tool_use".equals(type)) {
                    toolCalls.add(new ToolCall(
                            block.path("id").asText(),
                            block.path("name").asText(),
                            block.path("input").toString()));
                }
            }
        }
        return new CompletionResult(content.toString(), toolCalls);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.anthropic.com";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
