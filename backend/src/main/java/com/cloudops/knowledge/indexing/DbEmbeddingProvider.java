package com.cloudops.knowledge.indexing;

import com.cloudops.ai.provider.domain.AiProvider;
import com.cloudops.ai.runtime.OpenAiCompatRuntime;
import java.util.ArrayList;
import java.util.List;

public class DbEmbeddingProvider implements EmbeddingProvider {

    private final AiProvider provider;
    private final OpenAiCompatRuntime runtime;

    public DbEmbeddingProvider(AiProvider provider, OpenAiCompatRuntime runtime) {
        this.provider = provider;
        this.runtime = runtime;
    }

    @Override
    public String name() {
        return provider.getName();
    }

    @Override
    public int dimensions() {
        return provider.getEmbeddingDims() != null ? provider.getEmbeddingDims() : 1536;
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = runtime.embedBatch(texts, provider.getEmbeddingModel());
        for (float[] vector : vectors) {
            if (vector.length != dimensions()) {
                throw new EmbeddingException(
                        "Expected " + dimensions() + " dimensions, got " + vector.length
                                + ". Switch embedding provider or run POST /api/knowledge/reindex after updating dimensions.");
            }
        }
        return vectors;
    }
}
