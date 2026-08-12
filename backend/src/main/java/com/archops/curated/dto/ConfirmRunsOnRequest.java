package com.archops.curated.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRunsOnRequest(
        @NotBlank String containerId,
        @NotBlank String hostId
) {
}
