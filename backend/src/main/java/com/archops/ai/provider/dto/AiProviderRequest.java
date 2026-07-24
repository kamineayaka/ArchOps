package com.archops.ai.provider.dto;

import com.archops.ai.provider.domain.ProviderType;
import com.archops.ai.provider.domain.ReasoningEffort;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiProviderRequest(
        @NotBlank String name,
        @NotNull ProviderType providerType,
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        Integer embeddingDims,
        Boolean supportsChat,
        Boolean supportsEmbedding,
        Boolean enabled,
        Long timeoutMs,
        Integer maxOutputTokens,
        Integer contextWindow,
        Boolean reasoningEnabled,
        ReasoningEffort reasoningEffort) {}
