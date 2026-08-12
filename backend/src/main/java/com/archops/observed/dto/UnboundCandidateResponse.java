package com.archops.observed.dto;

import com.archops.observed.domain.UnboundReason;

import java.time.Instant;

public record UnboundCandidateResponse(
        String id,
        String sourceAgentId,
        String sourceHostId,
        String runtimeId,
        String name,
        UnboundReason reason,
        boolean upgradeChainPromised,
        Instant observedAt
) {
}
