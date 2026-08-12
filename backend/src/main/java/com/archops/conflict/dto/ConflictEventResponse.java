package com.archops.conflict.dto;

import com.archops.conflict.domain.ConflictEventType;

import java.time.Instant;
import java.util.Map;

public record ConflictEventResponse(
        String id,
        String conflictId,
        ConflictEventType eventType,
        String actorUserId,
        Map<String, Object> detail,
        Instant createdAt
) {
}
