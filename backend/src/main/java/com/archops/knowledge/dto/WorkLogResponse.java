package com.archops.knowledge.dto;

import java.time.Instant;
import java.util.List;

public record WorkLogResponse(
        Long id,
        String logType,
        String actorName,
        String summary,
        Instant createdAt,
        Long conversationId,
        Long userId,
        List<Long> assetIds,
        List<Long> groupIds,
        String level,
        boolean hypothesis,
        String source) {}
