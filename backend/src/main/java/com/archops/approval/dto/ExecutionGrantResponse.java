package com.archops.approval.dto;

import java.time.Instant;

public record ExecutionGrantResponse(
        Long id,
        Long conversationId,
        String toolName,
        Long assetId,
        String riskLevel,
        String pattern,
        Instant expiresAt,
        Instant createdAt) {}
