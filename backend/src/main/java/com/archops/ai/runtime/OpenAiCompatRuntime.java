package com.archops.ai.runtime;

import com.archops.ai.llm.LlmProvider.ChatMessage;
import com.archops.ai.llm.LlmProvider.CompletionResult;
import com.archops.ai.llm.LlmProvider.ToolCall;
import com.archops.ai.llm.LlmProvider.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class OpenAiCompatRuntime implements LlmRuntime {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;
    private final LlmGenerationConfig generation;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatRuntime(
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

    /** Backward-compatible constructor used by older tests. */
    public OpenAiCompatRuntime(String baseUrl, String apiKey, String model, long timeoutMs, ObjectMapper objectMapper) {
        this(baseUrl, apiKey, model, timeoutMs, null, objectMapper);
    }

    @Override
    public CompletionResult complete(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            String body = buildRequestBody(messages, tools, false);
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return new CompletionResult("[LLM error] " + response.statusCode(), List.of());
            }
            return parseResponse(response.body());
        } catch (Exception ex) {
            return new CompletionResult("[LLM request failed] " + ex.getMessage(), List.of());
        }
    }

    @Override
    public CompletionResult streamComplete(List<ChatMessage> messages, List<ToolDefinition> tools, Consumer<String> onToken) {
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallBuilder> toolBuilders = new LinkedHashMap<>();
        try {
            String body = buildRequestBody(messages, tools, true);
            httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofLines())
                    .body()
                    .forEach(line -> {
                        if (!line.startsWith("data: ") || line.equals("data: [DONE]")) {
                            return;
                        }
                        try {
                            JsonNode delta = objectMapper.readTree(line.substring(6))
                                    .path("choices").path(0).path("delta");
                            String token = delta.path("content").asText("");
                            if (!token.isBlank()) {
                                content.append(token);
                                onToken.accept(token);
                            }
                            JsonNode toolCalls = delta.path("tool_calls");
                            if (toolCalls.isArray()) {
                                for (JsonNode tc : toolCalls) {
                                    int index = tc.path("index").asInt(0);
                                    ToolCallBuilder builder = toolBuilders.computeIfAbsent(index, ToolCallBuilder::new);
                                    if (tc.hasNonNull("id")) {
                                        builder.id = tc.path("id").asText();
                                    }
                                    JsonNode fn = tc.path("function");
                                    if (fn.hasNonNull("name")) {
                                        builder.name = fn.path("name").asText();
                                    }
                                    if (fn.hasNonNull("arguments")) {
                                        builder.arguments.append(fn.path("arguments").asText(""));
                                    }
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
            return new ToolCall(callId, name, arguments.toString());
        }
    }

    public List<String> listModels() {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                            .header("Authorization", "Bearer " + apiKey)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from /models");
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<String> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText();
                    if (id != null && !id.isBlank()) {
                        models.add(id);
                    }
                }
            }
            return models;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() != null ? ex.getMessage() : "models request failed", ex);
        }
    }

    public float[] embed(String text, String embeddingModel) {
        return embedBatch(List.of(text), embeddingModel).getFirst();
    }

    public List<float[]> embedBatch(List<String> texts, String embeddingModel) {
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", embeddingModel);
            var input = root.putArray("input");
            texts.forEach(input::add);
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Embedding API error " + response.statusCode());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                float[] values = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    values[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(values);
            }
            if (vectors.size() != texts.size()) {
                throw new IllegalStateException("Embedding API returned " + vectors.size() + " vectors for " + texts.size() + " inputs");
            }
            return vectors;
        } catch (Exception ex) {
            throw new IllegalStateException("Embedding request failed: " + ex.getMessage(), ex);
        }
    }

    private String buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools, boolean stream) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", stream);
        int maxTokens = generation.effectiveMaxTokens(0);
        if (maxTokens > 0) {
            root.put("max_tokens", maxTokens);
        }
        if (generation.reasoningEnabled()) {
            String effort = generation.reasoningEffort().toOpenAiValue();
            if (effort != null) {
                root.put("reasoning_effort", effort);
            }
        }
        var msgs = root.putArray("messages");
        for (ChatMessage msg : messages) {
            var m = msgs.addObject();
            m.put("role", msg.role());
            m.put("content", msg.content());
        }
        if (tools != null && !tools.isEmpty()) {
            var toolsNode = root.putArray("tools");
            for (ToolDefinition tool : tools) {
                var t = toolsNode.addObject();
                t.put("type", "function");
                var fn = t.putObject("function");
                fn.put("name", tool.name());
                fn.put("description", tool.description());
                fn.put("parameters", objectMapper.readTree(tool.parametersJson()));
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    private CompletionResult parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choice = root.path("choices").path(0).path("message");
        String content = choice.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode tcNode = choice.path("tool_calls");
        if (tcNode.isArray()) {
            for (JsonNode tc : tcNode) {
                toolCalls.add(new ToolCall(
                        tc.path("id").asText(),
                        tc.path("function").path("name").asText(),
                        tc.path("function").path("arguments").asText()));
            }
        }
        return new CompletionResult(content, toolCalls);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
