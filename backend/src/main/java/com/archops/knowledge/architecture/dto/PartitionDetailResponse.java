package com.archops.knowledge.architecture.dto;

import java.time.Instant;
import java.util.List;

public record PartitionDetailResponse(
        Long id,
        String partitionKey,
        String title,
        boolean highImpact,
        Long latestRevisionId,
        Long latestVersion,
        String summary,
        String bodyMd,
        String structuredJson,
        Long createdBy,
        Instant revisedAt,
        List<FactResponse> facts) {}
