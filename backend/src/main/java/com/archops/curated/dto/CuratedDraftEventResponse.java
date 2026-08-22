package com.archops.curated.dto;

import com.archops.curated.domain.CuratedDraftEventType;

import java.time.Instant;
import java.util.Map;

public record CuratedDraftEventResponse(
        String id,
        String draftId,
        CuratedDraftEventType eventType,
        String actorUserId,
        Map<String, Object> detail,
        Instant createdAt
) {
}
