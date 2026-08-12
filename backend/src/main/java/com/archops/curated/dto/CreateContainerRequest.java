package com.archops.curated.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateContainerRequest(
        @NotBlank String name,
        /** Value stored as immutable label {@code archops.object_id=<objectId>}. */
        @NotBlank String objectId
) {
}
