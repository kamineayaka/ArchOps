package com.archops.observed.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Stores a probe task. {@code curatedExpected} is the only configured normal.
 */
public record CreateProbeTaskRequest(
        @NotBlank String hostId,
        @NotBlank String command,
        @NotNull @Min(0) Integer intervalSeconds,
        @NotNull JsonNode curatedExpected
) {
}
