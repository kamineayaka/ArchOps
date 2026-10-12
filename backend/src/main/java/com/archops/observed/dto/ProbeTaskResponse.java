package com.archops.observed.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Probe task read model. No cause-kind catalog (ADR-0048 / ADR-0047).
 */
public record ProbeTaskResponse(
        String id,
        String hostId,
        String command,
        int intervalSeconds,
        JsonNode curatedExpected,
        String judgment,
        String conflictId,
        boolean observationHollow,
        JsonNode observedValue,
        String evidence
) {
}
