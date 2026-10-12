package com.archops.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record AgentHeartbeatResponse(
        String agentId,
        String hostId,
        Instant acceptedAt,
        Freshness freshness,
        List<MatchedObserved> matched,
        List<AbsentObserved> absent,
        List<UnboundCandidate> unbound,
        List<IdentityLost> identityLost,
        List<ProbeTaskAssignment> probeTasks
) {
    public record Freshness(
            Instant lastHeartbeatAt,
            Instant lastSnapshotAt
    ) {
    }

    public record MatchedObserved(
            String curatedContainerId,
            String objectId,
            String observedHostId,
            String relationType
    ) {
    }

    public record AbsentObserved(
            String curatedContainerId,
            String objectId,
            String availability
    ) {
    }

    public record UnboundCandidate(
            String id,
            String reason,
            String runtimeId,
            String name,
            boolean upgradeChainPromised
    ) {
    }

    public record IdentityLost(
            String curatedObjectId,
            String objectId,
            boolean upgradeChainPromised
    ) {
    }

    /**
     * Task list pulled on heartbeat. The agent does not judge; the curated value
     * stays the control plane's only normal.
     */
    public record ProbeTaskAssignment(
            String id,
            String command,
            int intervalSeconds,
            JsonNode curatedExpected
    ) {
    }
}
