package com.archops.graph.dto;

import java.time.Instant;
import java.util.UUID;

public record TerminalDockItem(
        Long id,
        UUID elementId,
        Long assetId,
        String name,
        String kind,
        String host,
        boolean pinned,
        boolean hasSshCredential,
        Instant lastOpenedAt) {}
