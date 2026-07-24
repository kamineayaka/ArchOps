package com.archops.asset.dto;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.SshAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Create/update asset payload. Optional credential fields allow one-shot connectable create.
 * {@code description} is notes only — never Architecture SSOT.
 * Typed non-secret fields ({@code database}, {@code k8sMode}, {@code apiServerUrl}) merge into metadata.
 */
public record AssetRequest(
        @NotBlank String name,
        @NotNull AssetKind kind,
        String host,
        Integer port,
        String metadata,
        Long parentId,
        Boolean enabled,
        String description,
        Long groupId,
        String username,
        SshAuthType authType,
        String secret,
        List<Long> jumpAssetIds,
        String database,
        String k8sMode,
        String apiServerUrl) {}
