package com.archops.observed.dto;

import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.CuratedObjectResponse;

/**
 * 规范问法「实际在哪」— observed track answer with curated value on the same screen (P2).
 */
public record ActualWhereResponse(
        String question,
        String track,
        CuratedRelationType relationType,
        String relationLabel,
        CuratedObjectResponse subject,
        ObservedValue observedValue,
        CuratedHostValue curatedValue
) {
    public record ObservedValue(
            /**
             * PRESENT / ABSENT / HOLLOW.
             * HOLLOW = no currently usable observed value (never written or later timeout).
             */
            String availability,
            String hostId,
            String hostName
    ) {
    }

    public record CuratedHostValue(
            String hostId,
            String hostName
    ) {
    }
}
