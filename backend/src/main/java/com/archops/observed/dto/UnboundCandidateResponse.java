package com.archops.observed.dto;

import com.archops.observed.domain.UnboundReason;

import java.time.Instant;
import java.util.Map;

public record UnboundCandidateResponse(
        String id,
        String sourceAgentId,
        String sourceHostId,
        String runtimeId,
        String name,
        Map<String, String> labels,
        UnboundReason reason,
        boolean upgradeChainPromised,
        Instant observedAt
) {
}
