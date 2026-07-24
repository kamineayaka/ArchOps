package com.archops.ai.provider.dto;

import com.archops.ai.provider.domain.ProviderType;
import com.archops.ai.provider.domain.ReasoningEffort;
import java.time.Instant;

public record AiProviderResponse(
        Long id,
        String name,
        ProviderType providerType,
        String baseUrl,
        String apiKeyMasked,
        String chatModel,
        String embeddingModel,
        Integer embeddingDims,
        boolean supportsChat,
        boolean supportsEmbedding,
        boolean enabled,
        long timeoutMs,
        int maxOutputTokens,
        int contextWindow,
        boolean reasoningEnabled,
        ReasoningEffort reasoningEffort,
        boolean defaultChat,
        boolean defaultEmbedding,
        Instant createdAt,
        Instant updatedAt) {}
