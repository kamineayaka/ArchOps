package com.archops.knowledge.hybrid;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Hit counters for hybrid retrieval legs (graph / facts / vector). */
@Component
public class HybridRetrievalMetrics {

    private final Counter graphHits;
    private final Counter factHits;
    private final Counter vectorHits;

    public HybridRetrievalMetrics(MeterRegistry meterRegistry) {
        this.graphHits = Counter.builder("archops_hybrid_rag_graph_hits")
                .description("Nodes included in graph neighborhood retrieval")
                .register(meterRegistry);
        this.factHits = Counter.builder("archops_hybrid_rag_fact_hits")
                .description("Architecture fact lines included in hybrid retrieval")
                .register(meterRegistry);
        this.vectorHits = Counter.builder("archops_hybrid_rag_vector_hits")
                .description("Vector text-memory chunks included in hybrid retrieval")
                .register(meterRegistry);
    }

    public void record(int graphNodes, int facts, int vectors) {
        if (graphNodes > 0) {
            graphHits.increment(graphNodes);
        }
        if (facts > 0) {
            factHits.increment(facts);
        }
        if (vectors > 0) {
            vectorHits.increment(vectors);
        }
    }
}
