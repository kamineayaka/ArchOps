package com.archops.knowledge.retrieval;

import java.util.List;

/**
 * Optional retrieval scope for architecture-aware RAG filtering.
 * Empty / null fields mean "no constraint on that dimension".
 */
public record RagScope(List<Long> assetIds, List<Long> groupIds, List<String> partitionKeys) {

    public static RagScope empty() {
        return new RagScope(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return (assetIds == null || assetIds.isEmpty())
                && (groupIds == null || groupIds.isEmpty())
                && (partitionKeys == null || partitionKeys.isEmpty());
    }
}
