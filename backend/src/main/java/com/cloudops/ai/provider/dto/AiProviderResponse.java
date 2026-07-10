package com.cloudops.ai.provider.dto;

import com.cloudops.ai.provider.domain.ProviderType;
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
        boolean defaultChat,
        boolean defaultEmbedding,
        Instant createdAt,
        Instant updatedAt) {}
