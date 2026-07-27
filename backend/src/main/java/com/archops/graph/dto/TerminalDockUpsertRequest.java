package com.archops.graph.dto;

import java.util.UUID;

public record TerminalDockUpsertRequest(UUID elementId, Long assetId, Boolean pinned) {}
