package com.cloudops.ai.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_ai_settings")
public class PlatformAiSettings {

    @Id
    private Short id = 1;

    @Column(name = "default_chat_provider_id")
    private Long defaultChatProviderId;

    @Column(name = "default_embedding_provider_id")
    private Long defaultEmbeddingProviderId;

    @Column(name = "rag_enabled", nullable = false)
    private boolean ragEnabled = true;

    @Column(name = "rag_top_k", nullable = false)
    private int ragTopK = 5;

    @Column(name = "rag_min_similarity", nullable = false)
    private double ragMinSimilarity = 0.35;

    public Short getId() { return id; }
    public Long getDefaultChatProviderId() { return defaultChatProviderId; }
    public void setDefaultChatProviderId(Long defaultChatProviderId) { this.defaultChatProviderId = defaultChatProviderId; }
    public Long getDefaultEmbeddingProviderId() { return defaultEmbeddingProviderId; }
    public void setDefaultEmbeddingProviderId(Long defaultEmbeddingProviderId) { this.defaultEmbeddingProviderId = defaultEmbeddingProviderId; }
    public boolean isRagEnabled() { return ragEnabled; }
    public void setRagEnabled(boolean ragEnabled) { this.ragEnabled = ragEnabled; }
    public int getRagTopK() { return ragTopK; }
    public void setRagTopK(int ragTopK) { this.ragTopK = ragTopK; }
    public double getRagMinSimilarity() { return ragMinSimilarity; }
    public void setRagMinSimilarity(double ragMinSimilarity) { this.ragMinSimilarity = ragMinSimilarity; }
}
