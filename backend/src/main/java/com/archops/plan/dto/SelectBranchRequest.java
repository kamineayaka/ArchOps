package com.archops.plan.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectBranchRequest(
        @NotBlank String forkId,
        String diagnosisId
) {
}
