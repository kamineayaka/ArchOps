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
        Instant pendingCloseAt,
        Instant closedAt,
        Instant suspendedAt,
        /**
         * True when status is PENDING_CLOSE — reminder stays visible even if 已知悉.
         */
        boolean pendingCloseReminderVisible,
        /**
         * True when observation is 空洞 (e.g. SUSPENDED after heartbeat timeout) —
         * observedValue must not be trusted as 实际.
         */
        boolean observationHollow,
        /**
         * True when the merge-key subject currently has an 身份失联 mark.
         * Read-model only — not a ConflictStatus, not observed_fact.availability.
         */
        boolean identityLost,
        /**
         * NOT_STARTED | PENDING | READY | FAILED — diagnosis is async and never blocks warning.
         */
        String diagnosisStatus,
        Collaboration collaboration
) {
    public enum ConflictStatusView {
        OPEN,
        PENDING_CLOSE,
        CLOSED,
        SUSPENDED
    }

    public record MergeKey(
            String subjectId,
            CuratedRelationType relationType,
            String relationLabel
    ) {
    }

    /**
     * Track display. availability is PRESENT | ABSENT | HOLLOW (string for hollow without enum drift).
     */
    public record TrackValue(
            String availability,
            String hostId,
            String hostName
    ) {
        public static TrackValue present(String hostId, String hostName) {
            return new TrackValue("PRESENT", hostId, hostName);
        }

        public static TrackValue absent() {
            return new TrackValue("ABSENT", null, null);
        }

        public static TrackValue hollow() {
            return new TrackValue("HOLLOW", null, null);
        }

        /** Conflict GET projection of 身份失联: not PRESENT, not HOLLOW, hostId JSON null. */
        public static TrackValue identityLost() {
            return new TrackValue("IDENTITY_LOST", null, null);
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
