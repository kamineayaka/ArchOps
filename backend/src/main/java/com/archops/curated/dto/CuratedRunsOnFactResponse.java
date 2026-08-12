package com.archops.curated.dto;

import com.archops.curated.domain.CuratedRelationType;

import java.time.Instant;

public record CuratedRunsOnFactResponse(
        String id,
        CuratedRelationType relationType,
        String relationLabel,
        CuratedObjectResponse subject,
        CuratedObjectResponse target,
        Instant createdAt
) {
}
