package com.archops.conflict.dto;

import com.archops.conflict.domain.HandlerAcceptance;
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
         * Warnings are independent of diagnosis.
         * NOT_STARTED until ticket 06 lands.
         */
        String diagnosisStatus,
        Collaboration collaboration
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

    /**
     * 已知悉 / 冲突归属 / 冲突处理人 collaboration snapshot.
     */
    public record Collaboration(
            boolean acknowledged,
            Instant acknowledgedAt,
            String ownerUserId,
            String handlerUserId,
            HandlerAcceptance handlerAcceptance
    ) {
    }
}
