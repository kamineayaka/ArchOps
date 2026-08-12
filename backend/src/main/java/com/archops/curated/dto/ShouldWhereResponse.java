package com.archops.curated.dto;

import com.archops.curated.domain.CuratedRelationType;

/**
 * 规范问法「应该在哪」— answers only from curated track (ideal).
 */
public record ShouldWhereResponse(
        String question,
        String track,
        CuratedRelationType relationType,
        String relationLabel,
        CuratedObjectResponse subject,
        CuratedHostValue curatedValue
) {
    public record CuratedHostValue(
            String hostId,
            String hostName
    ) {
    }
}
