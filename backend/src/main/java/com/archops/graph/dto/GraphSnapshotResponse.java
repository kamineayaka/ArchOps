package com.archops.graph.dto;

import java.util.List;
import java.util.Map;

public record GraphSnapshotResponse(
        boolean graphEnabled,
        long graphVersion,
        List<GraphNodeDto> nodes,
        List<GraphEdgeDto> edges) {

    public record GraphNodeDto(
            String elementId,
            Long pgAssetId,
            String kind,
            String name,
            String host,
            Integer port,
            boolean enabled,
            boolean hasCredential,
            String slug,
            List<String> labels,
            Map<String, Object> properties) {}

    public record GraphEdgeDto(
            String elementId,
            String type,
            String fromElementId,
            String toElementId,
            Map<String, Object> properties) {}
}
