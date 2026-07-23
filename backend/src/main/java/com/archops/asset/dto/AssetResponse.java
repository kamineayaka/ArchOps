package com.archops.asset.dto;

import com.archops.asset.domain.AssetKind;
import java.time.Instant;
import java.util.List;

public record AssetResponse(
        Long id,
        String name,
        AssetKind kind,
        String host,
        Integer port,
        String metadata,
        String description,
        Long parentId,
        boolean enabled,
        boolean hasSshCredential,
        List<Long> jumpAssetIds,
        Instant createdAt,
        Instant updatedAt) {}
