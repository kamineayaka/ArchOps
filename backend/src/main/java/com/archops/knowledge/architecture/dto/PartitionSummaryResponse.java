package com.archops.knowledge.architecture.dto;

public record PartitionSummaryResponse(
        Long id,
        String partitionKey,
        String title,
        boolean highImpact,
        Long latestVersion,
        long activeFactCount) {}
