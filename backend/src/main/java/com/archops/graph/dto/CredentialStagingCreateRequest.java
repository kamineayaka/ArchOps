package com.archops.graph.dto;

import com.archops.asset.domain.SshAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CredentialStagingCreateRequest(
        @NotBlank String username,
        @NotNull SshAuthType authType,
        @NotBlank String secret,
        Long assetId,
        String tempRef) {}
