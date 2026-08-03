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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Map<Integer, ToolCallBuilder> toolBuilders = new LinkedHashMap<>();
        try {
            String body = buildRequestBody(messages, tools, true);
            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                            .header("Content-Type", "application/json")
                            .header("x-api-key", apiKey)
                            .header("anthropic-version", ANTHROPIC_VERSION)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                String err = "[Anthropic stream error] " + response.statusCode();
                onToken.accept(err);
                return new CompletionResult(err, List.of());
            }
            response.body().forEach(line -> {
                if (!line.startsWith("data: ")) {
                    return;
                }
                try {
                    JsonNode event = objectMapper.readTree(line.substring(6));
                    String type = event.path("type").asText();
                    if ("content_block_start".equals(type)) {
                        JsonNode block = event.path("content_block");
                        if ("tool_use".equals(block.path("type").asText())) {
                            int index = event.path("index").asInt(0);
                            ToolCallBuilder builder = toolBuilders.computeIfAbsent(index, ToolCallBuilder::new);
                            builder.id = block.path("id").asText();
                            builder.name = block.path("name").asText();
                        }
                    } else if ("content_block_delta".equals(type)) {
                        JsonNode delta = event.path("delta");
                        String deltaType = delta.path("type").asText();
                        if ("text_delta".equals(deltaType) || delta.hasNonNull("text")) {
                            String text = delta.path("text").asText("");
                            if (!text.isBlank()) {
                                content.append(text);
                                onToken.accept(text);
                            }
                        } else if ("input_json_delta".equals(deltaType)) {
                            int index = event.path("index").asInt(0);
                            ToolCallBuilder builder = toolBuilders.computeIfAbsent(index, ToolCallBuilder::new);
                            builder.arguments.append(delta.path("partial_json").asText(""));
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            List<ToolCall> toolCalls = toolBuilders.values().stream()
                    .filter(builder -> builder.name != null && !builder.name.isBlank())
                    .map(ToolCallBuilder::build)
                    .toList();
            return new CompletionResult(content.toString(), toolCalls);
        } catch (Exception ex) {
            String err = "[stream failed] " + ex.getMessage();
            onToken.accept(err);
            return new CompletionResult(err, List.of());
        }
    }

    private static final class ToolCallBuilder {
        private final int index;
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolCallBuilder(int index) {
            this.index = index;
        }

        private ToolCall build() {
            String callId = id != null && !id.isBlank() ? id : "stream-call-" + index;
            String args = arguments.length() > 0 ? arguments.toString() : "{}";
            return new ToolCall(callId, name, args);
        }
    }

    public List<String> listModels() {
        return KNOWN_MODELS;
    }

    /** Package-visible for unit tests. */
    String buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools, boolean stream) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        int maxTokens = generation.effectiveMaxTokens(4096);
        root.put("max_tokens", maxTokens);
        root.put("stream", stream);
        if (generation.reasoningEnabled()) {
            int budget = generation.reasoningEffort().anthropicBudgetTokens();
            if (budget > 0) {
                // Anthropic requires budget_tokens < max_tokens
                int safeBudget = Math.min(budget, Math.max(1, maxTokens - 1));
                ObjectNode thinking = root.putObject("thinking");
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", safeBudget);
            }
        }

        String system = "";
        ArrayNode anthropicMessages = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.role())) {
                system = msg.content() != null ? msg.content() : "";
                continue;
            }
            if ("tool".equals(msg.role())) {
                ObjectNode toolResult = objectMapper.createObjectNode();
                toolResult.put("role", "user");
                ArrayNode content = toolResult.putArray("content");
                ObjectNode block = content.addObject();
                block.put("type", "tool_result");
                String toolUseId = msg.toolCallId() != null && !msg.toolCallId().isBlank()
                        ? msg.toolCallId()
                        : "tool";
                block.put("tool_use_id", toolUseId);
                block.put("content", msg.content() != null ? msg.content() : "");
                anthropicMessages.add(toolResult);
                continue;
            }
            List<ToolCall> toolCalls = msg.toolCalls();
            if ("assistant".equals(msg.role()) && toolCalls != null && !toolCalls.isEmpty()) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("role", "assistant");
                ArrayNode content = node.putArray("content");
                if (msg.content() != null && !msg.content().isBlank()) {
                    ObjectNode text = content.addObject();
                    text.put("type", "text");
                    text.put("text", msg.content());
                }
                for (ToolCall tc : toolCalls) {
                    ObjectNode toolUse = content.addObject();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", tc.id() != null ? tc.id() : "");
                    toolUse.put("name", tc.name());
                    toolUse.set("input", parseToolArguments(tc.arguments()));
                }
                anthropicMessages.add(node);
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", "assistant".equals(msg.role()) ? "assistant" : "user");
            node.put("content", msg.content() != null ? msg.content() : "");
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

    private JsonNode parseToolArguments(String arguments) {
        try {
            return objectMapper.readTree(arguments != null && !arguments.isBlank() ? arguments : "{}");
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
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
