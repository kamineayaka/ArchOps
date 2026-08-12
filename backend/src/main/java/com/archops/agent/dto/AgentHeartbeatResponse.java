package com.archops.agent.dto;

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
        List<IdentityLost> identityLost
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
}
