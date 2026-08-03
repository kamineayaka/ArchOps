package com.archops.asset.dto;

import com.archops.asset.domain.AssetKind;
import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        Long id,
        UUID elementId,
        String name,
        AssetKind kind,
        String host,
        Integer port,
        String metadata,
        String description,
        boolean enabled,
        @com.fasterxml.jackson.annotation.JsonProperty("hasCredential")
        @com.fasterxml.jackson.annotation.JsonAlias({"hasSshCredential"})
        boolean hasSshCredential,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {}
