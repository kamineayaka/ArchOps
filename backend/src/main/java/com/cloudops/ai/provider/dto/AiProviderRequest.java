package com.cloudops.ai.provider.dto;

import com.cloudops.ai.provider.domain.ProviderType;
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
        Long timeoutMs) {}
