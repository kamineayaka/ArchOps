package com.archops.conflict.dto;

import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.observed.domain.ObservedAvailability;

import java.time.Instant;
import java.util.List;

public record ConflictCaseResponse(
        String id,
        ConflictStatusView status,
        MergeKey mergeKey,
        CuratedObjectResponse subject,
        TrackValue curatedValue,
        TrackValue observedValue,
        List<LineageStep> observedLineage,
        Instant firstWarnedAt,
        Instant updatedAt,
        /**
         * Ticket 04: warnings are independent of diagnosis.
         * Always NOT_STARTED here — diagnosis lands in later tickets.
         */
        String diagnosisStatus
) {
    public enum ConflictStatusView {
        OPEN
    }

    public record MergeKey(
            String subjectId,
            CuratedRelationType relationType,
            String relationLabel
    ) {
    }

    public record TrackValue(
            ObservedAvailability availability,
            String hostId,
            String hostName
    ) {
        public static TrackValue present(String hostId, String hostName) {
            return new TrackValue(ObservedAvailability.PRESENT, hostId, hostName);
        }

        public static TrackValue absent() {
            return new TrackValue(ObservedAvailability.ABSENT, null, null);
        }
    }

    public record LineageStep(
            ObservedAvailability availability,
            String hostId,
            String hostName,
            Instant at
    ) {
    }
}
