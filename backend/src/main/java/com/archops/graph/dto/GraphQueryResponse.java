package com.archops.graph.dto;

import java.util.List;
import java.util.Map;

public record GraphQueryResponse(
        List<String> columns,
        List<Map<String, Object>> rows,
        List<String> matchedElementIds,
        long elapsedMs) {}
