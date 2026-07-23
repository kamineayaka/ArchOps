package com.archops.ai.dto;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        Long id,
        String title,
        List<Long> targetAssetIds,
        List<Long> targetGroupIds,
        List<Long> resolvedAssetIds,
        Instant createdAt,
        Instant updatedAt) {}
