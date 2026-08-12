package com.archops.curated.dto;

import com.archops.curated.domain.HostSshSecretKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Upsert host SSH credential. {@code secret} is accepted once and encrypted — never echoed back.
 */
public record UpsertHostSshCredentialRequest(
        @NotBlank String connectHost,
        @Min(1) @Max(65535) Integer connectPort,
        @NotBlank String username,
        @NotBlank String secret,
        @NotNull HostSshSecretKind secretKind
) {
}
