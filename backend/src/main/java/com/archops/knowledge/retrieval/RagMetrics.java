package com.archops.knowledge.retrieval;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Vector RAG observability: chunks returned per retrieval call. */
@Component
public class RagMetrics {

    private final Counter hitsCounter;

    public RagMetrics(MeterRegistry meterRegistry) {
        this.hitsCounter = Counter.builder("rag_hits_per_chat")
                .description("RAG chunks returned per retrieval call")
                .register(meterRegistry);
    }

    public void recordHits(int hits) {
        if (hits > 0) {
            hitsCounter.increment(hits);
        }
    }
}
