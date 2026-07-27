package com.archops.graph.dto;

import java.time.Instant;
import java.util.UUID;

public record CredentialStagingResponse(UUID stagingId, Instant expiresAt, String tempRef, Long assetId) {}
