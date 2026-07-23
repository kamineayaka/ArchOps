package com.archops.knowledge.retrieval;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Simple RAG observability: hits per chat retrieval.
 */
@Component
public class RagMetrics {

    private final AtomicLong hitsPerChat = new AtomicLong();
    private final Counter hitsCounter;

    public RagMetrics(MeterRegistry meterRegistry) {
        this.hitsCounter = Counter.builder("rag_hits_per_chat")
                .description("RAG chunks returned per retrieval call")
                .register(meterRegistry);
    }

    public void recordHits(int hits) {
        hitsPerChat.addAndGet(hits);
        if (hits > 0) {
            hitsCounter.increment(hits);
        }
    }

    public long totalHits() {
        return hitsPerChat.get();
    }
}
