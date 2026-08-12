package com.archops.curated.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateHostRequest(
        @NotBlank String name
) {
}
