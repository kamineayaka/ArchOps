package com.archops.ai.provider.dto;

/**
 * Model id plus optional default generation limits (OpsKat AIModelInfo-aligned).
 * Missing / unknown defaults use {@code null} or {@code 0}.
 */
public record AiModelInfo(String id, Integer maxOutputTokens, Integer contextWindow) {

    public static AiModelInfo of(String id) {
        return new AiModelInfo(id, null, null);
    }

    public static AiModelInfo of(String id, int maxOutputTokens, int contextWindow) {
        return new AiModelInfo(id, maxOutputTokens, contextWindow);
    }
}
