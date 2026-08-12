package com.archops.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Agent heartbeat (+ optional snapshot) ingest payload.
 * Contract: {@code docs/contracts/agent-heartbeat-snapshot.md}.
 */
public record AgentHeartbeatRequest(
        @NotBlank String agentId,
        /** Curated physical host id this agent runs on. */
        @NotBlank String hostId,
        String sentAt,
        @Valid SnapshotPayload snapshot
) {
    public record SnapshotPayload(
            List<@Valid SnapshotContainer> containers,
            /** Immutable object ids explicitly asserted missing → 观测消失 (ABSENT). */
            List<String> absentObjectIds,
            /**
             * Curated container object ids whose label matching clue is lost.
             * Marks 身份失联; does not promise conflict upgrade chain.
             */
            List<String> identityLostObjectIds
    ) {
    }

    public record SnapshotContainer(
            String runtimeId,
            String name,
            Map<String, String> labels
    ) {
    }
}
