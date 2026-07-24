package com.archops.ai.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_provider")
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "api_key_cipher")
    private byte[] apiKeyCipher;

    @Column(name = "api_key_iv")
    private byte[] apiKeyIv;

    @Column(name = "chat_model", length = 128)
    private String chatModel;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedding_dims")
    private Integer embeddingDims;

    @Column(name = "supports_chat", nullable = false)
    private boolean supportsChat = true;

    @Column(name = "supports_embedding", nullable = false)
    private boolean supportsEmbedding;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "timeout_ms", nullable = false)
    private long timeoutMs = 60_000;

    /** 0 = use runtime default max tokens. */
    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens = 0;

    /** 0 = no explicit context budget. */
    @Column(name = "context_window", nullable = false)
    private int contextWindow = 0;

    @Column(name = "reasoning_enabled", nullable = false)
    private boolean reasoningEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "reasoning_effort", nullable = false, length = 16)
    private ReasoningEffort reasoningEffort = ReasoningEffort.NONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProviderType getProviderType() { return providerType; }
    public void setProviderType(ProviderType providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public byte[] getApiKeyCipher() { return apiKeyCipher; }
    public void setApiKeyCipher(byte[] apiKeyCipher) { this.apiKeyCipher = apiKeyCipher; }
    public byte[] getApiKeyIv() { return apiKeyIv; }
    public void setApiKeyIv(byte[] apiKeyIv) { this.apiKeyIv = apiKeyIv; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDims() { return embeddingDims; }
    public void setEmbeddingDims(Integer embeddingDims) { this.embeddingDims = embeddingDims; }
    public boolean isSupportsChat() { return supportsChat; }
    public void setSupportsChat(boolean supportsChat) { this.supportsChat = supportsChat; }
    public boolean isSupportsEmbedding() { return supportsEmbedding; }
    public void setSupportsEmbedding(boolean supportsEmbedding) { this.supportsEmbedding = supportsEmbedding; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getContextWindow() { return contextWindow; }
    public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; }
    public boolean isReasoningEnabled() { return reasoningEnabled; }
    public void setReasoningEnabled(boolean reasoningEnabled) { this.reasoningEnabled = reasoningEnabled; }
    public ReasoningEffort getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(ReasoningEffort reasoningEffort) { this.reasoningEffort = reasoningEffort; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
