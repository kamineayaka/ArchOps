package com.archops.graph.dto;

import java.util.List;
import java.util.Map;

public record GraphPlanResponse(
        long baseGraphVersion,
        long partitionBaseVersion,
        String partitionKey,
        String changeSetJson,
        String estimatedRisk,
        List<String> warnings,
        Map<String, Object> preview) {}
