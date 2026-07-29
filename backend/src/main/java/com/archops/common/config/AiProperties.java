package com.archops.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archops.ai")
public record AiProperties(
        String defaultProvider,
        OpenAiCompatProviderConfig openaiCompat,
        OllamaProviderConfig ollama,
        /** ReAct loop cap; default 8, clamped to 3–15. */
        Integer maxIterations) {

    public AiProperties {
        if (maxIterations == null || maxIterations <= 0) {
            maxIterations = 8;
        }
    }

    public int clampedMaxIterations() {
        return Math.max(3, Math.min(15, maxIterations));
    }

    public record OpenAiCompatProviderConfig(String baseUrl, String apiKey, String model, long timeoutMs) {}

    public record OllamaProviderConfig(String baseUrl, String model, long timeoutMs) {}
}
