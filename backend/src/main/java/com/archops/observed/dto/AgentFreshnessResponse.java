package com.archops.observed.dto;

import java.time.Instant;

public record AgentFreshnessResponse(
        String agentId,
        String hostId,
        Instant lastHeartbeatAt,
        Instant lastSnapshotAt
) {
}
