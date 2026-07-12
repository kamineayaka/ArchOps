package com.cloudops.ai.dto;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        Long id,
        String title,
        List<Long> targetAssetIds,
        Instant createdAt,
        Instant updatedAt) {}
