package com.archops.observed.dto;

import java.util.List;

public record HeartbeatTimeoutScanResponse(
        int staleAgents,
        int hollowedFacts,
        List<String> affectedSubjectIds,
        List<String> suspendedConflictIds,
        List<String> voidedPlanIds
) {
}
