package com.archops.knowledge.architecture.dto;

import java.util.List;

public record ArchitectureViewResponse(
        List<PartitionDetailResponse> partitions,
        String assembledMarkdown) {}
