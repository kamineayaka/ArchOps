package com.cloudops.ai.provider.dto;

public record PlatformAiSettingsRequest(
        Long defaultChatProviderId,
        Long defaultEmbeddingProviderId,
        Boolean ragEnabled,
        Integer ragTopK,
        Double ragMinSimilarity) {}
