package com.archops.observed.dto;

import java.time.Instant;

public record IdentityLostResponse(
        String curatedObjectId,
        String reason,
        Instant markedAt,
        String sourceAgentId,
        String sourceHostId,
        boolean upgradeChainPromised
) {
}
