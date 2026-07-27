package com.archops.graph.changeset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** GraphChangeSet schemaVersion=1 — see docs/graph-ssot-design.md */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphChangeSet(
        int schemaVersion,
        String changeSetId,
        Long baseGraphVersion,
        List<GraphOp> ops,
        List<PgSideEffect> pgSideEffects,
        List<String> invariants,
        Map<String, Integer> stats) {

    public GraphChangeSet {
        if (schemaVersion <= 0) {
            schemaVersion = 1;
        }
        ops = ops != null ? List.copyOf(ops) : List.of();
        pgSideEffects = pgSideEffects != null ? List.copyOf(pgSideEffects) : List.of();
        invariants = invariants != null ? List.copyOf(invariants) : List.of();
    }

    public boolean isEmpty() {
        return ops.isEmpty() && (pgSideEffects == null || pgSideEffects.isEmpty());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphOp(
            String op,
            String opId,
            String tempId,
            List<String> labels,
            Map<String, Object> properties,
            GraphRef ref,
            GraphRef from,
            GraphRef to,
            String type,
            Map<String, Object> set,
            List<String> unset,
            List<String> addLabels,
            List<String> removeLabels,
            String mode,
            String reason,
            String tag,
            String tagSlug,
            GraphRef tagRef,
            Boolean tagNode,
            Boolean soft,
            JsonNode pg,
            String riskHint) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphRef(String elementId, Long pgAssetId, String tempId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PgSideEffect(
            String effect,
            Long pgAssetId,
            String tempId,
            String credentialStagingId,
            String note) {}
}
