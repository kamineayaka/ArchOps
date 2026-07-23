package com.archops.knowledge.architecture.dto;

import java.time.Instant;

public record FactResponse(
        Long id,
        String factType,
        String subject,
        String predicate,
        String object,
        Long assetId,
        Double confidence,
        String status,
        String provenanceJson,
        Long revisionId,
        Instant createdAt) {}
