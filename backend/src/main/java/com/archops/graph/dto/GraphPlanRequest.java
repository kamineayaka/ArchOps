package com.archops.graph.dto;

import java.util.List;
import java.util.Map;

/** Canvas / agent draft → compiled ChangeSet preview (plan mode). */
public record GraphPlanRequest(
        String summary,
        List<Map<String, Object>> ops,
        List<Map<String, Object>> pgSideEffects) {}
