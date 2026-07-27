package com.archops.knowledge.hybrid;

import java.util.List;

/**
 * Assembled hybrid retrieval payload for agent prompt slots.
 * Graph + architecture facts are primary; textMemory is scoped vector RAG.
 */
public record HybridRetrievalResult(
        String graphNeighborhood,
        String architectureFacts,
        String textMemory,
        List<Long> scopedAssetIds,
        List<String> partitionKeys,
        int graphNodeHits,
        int factLineHits,
        int vectorHits,
        boolean graphAvailable) {

    public static HybridRetrievalResult empty() {
        return new HybridRetrievalResult(
                "(graph unavailable or empty)",
                "(no active facts)",
                "(no scoped hits)",
                List.of(),
                List.of(),
                0,
                0,
                0,
                false);
    }
}
