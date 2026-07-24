package com.archops.ai.provider.service;

import com.archops.ai.provider.domain.AiProvider;
import com.archops.ai.provider.domain.ProviderType;
import com.archops.ai.provider.domain.ReasoningEffort;
import com.archops.ai.provider.dto.AiModelInfo;
import com.archops.ai.provider.dto.AiProviderRequest;
import com.archops.ai.provider.dto.AiProviderResponse;
import com.archops.ai.provider.dto.ModelDefaultsResponse;
import com.archops.ai.provider.repository.AiProviderRepository;
import com.archops.ai.runtime.AnthropicRuntime;
import com.archops.ai.runtime.LlmGenerationConfig;
import com.archops.ai.runtime.LlmRuntimeFactory;
import com.archops.ai.runtime.OpenAiCompatRuntime;
import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderService {

    private final AiProviderRepository repository;
    private final PlatformAiSettingsService settingsService;
    private final LlmRuntimeFactory runtimeFactory;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ModelDefaultsCatalog modelDefaultsCatalog;

    public AiProviderService(
            AiProviderRepository repository,
            PlatformAiSettingsService settingsService,
            LlmRuntimeFactory runtimeFactory,
            AuditService auditService,
            ObjectMapper objectMapper,
            ModelDefaultsCatalog modelDefaultsCatalog) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.runtimeFactory = runtimeFactory;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.modelDefaultsCatalog = modelDefaultsCatalog;
    }

    @Transactional(readOnly = true)
    public List<AiProviderResponse> list() {
        var settings = settingsService.getSettings();
        return repository.findAll().stream()
                .map(p -> toResponse(p, settings.getDefaultChatProviderId(), settings.getDefaultEmbeddingProviderId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiProviderResponse> listEnabled() {
        var settings = settingsService.getSettings();
        return repository.findByEnabledTrueOrderByNameAsc().stream()
                .filter(AiProvider::isSupportsChat)
                .map(p -> toResponse(p, settings.getDefaultChatProviderId(), settings.getDefaultEmbeddingProviderId()))
                .toList();
    }

    @Transactional
    public AiProviderResponse create(AiProviderRequest request, Long actorId, String actorName) {
        validateRequest(request, true);
        AiProvider provider = new AiProvider();
        applyRequest(provider, request, true);
        provider = repository.save(provider);
        if (settingsService.getSettings().getDefaultChatProviderId() == null && provider.isSupportsChat()) {
            settingsService.setDefaultChatProvider(provider.getId());
        }
        if (settingsService.getSettings().getDefaultEmbeddingProviderId() == null && provider.isSupportsEmbedding()) {
            settingsService.setDefaultEmbeddingProvider(provider.getId());
        }
        audit(actorId, actorName, "ai.provider.create", provider.getId());
        return toResponse(provider);
    }

    @Transactional
    public AiProviderResponse update(Long id, AiProviderRequest request, Long actorId, String actorName) {
        AiProvider provider = findOrThrow(id);
        validateRequest(request, false);
        applyRequest(provider, request, false);
        provider.setUpdatedAt(Instant.now());
        provider = repository.save(provider);
        audit(actorId, actorName, "ai.provider.update", provider.getId());
        return toResponse(provider);
    }

    @Transactional
    public void delete(Long id, Long actorId, String actorName) {
        settingsService.requireProviderNotDefault(id);
        AiProvider provider = findOrThrow(id);
        repository.delete(provider);
        audit(actorId, actorName, "ai.provider.delete", id);
    }

    @Transactional(readOnly = true)
    public String testConnection(Long id) {
        AiProvider provider = findOrThrow(id);
        try {
            runtimeFactory.createChatRuntime(provider).complete(
                    List.of(com.archops.ai.llm.LlmProvider.ChatMessage.user("ping")),
                    List.of());
            return "ok";
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            // Never log or echo API keys.
            return "failed: " + sanitizeError(msg);
        }
    }

    @Transactional(readOnly = true)
    public List<AiModelInfo> listModels(Long id) {
        AiProvider provider = findOrThrow(id);
        String apiKey = runtimeFactory.decryptApiKey(provider);
        LlmGenerationConfig config = LlmGenerationConfig.from(provider);
        try {
            List<String> ids = switch (provider.getProviderType()) {
                case OPENAI_COMPAT -> new OpenAiCompatRuntime(
                        provider.getBaseUrl(), apiKey, provider.getChatModel(), provider.getTimeoutMs(),
                        config, objectMapper).listModels();
                case ANTHROPIC -> new AnthropicRuntime(
                        provider.getBaseUrl(), apiKey, provider.getChatModel(), provider.getTimeoutMs(),
                        config, objectMapper).listModels();
            };
            return ids.stream().map(modelDefaultsCatalog::enrich).toList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    HttpStatus.BAD_GATEWAY,
                    "FETCH_MODELS_FAILED",
                    "获取模型失败: " + sanitizeError(ex.getMessage()));
        }
    }

    /** Catalog lookup; unknown models return zeros (never 500). */
    public ModelDefaultsResponse modelDefaults(String model) {
        return modelDefaultsCatalog.defaultsFor(model);
    }

    @Transactional(readOnly = true)
    public AiProvider findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "AI Provider 不存在"));
    }

    private void applyRequest(AiProvider provider, AiProviderRequest request, boolean isCreate) {
        provider.setName(request.name());
        provider.setProviderType(request.providerType());
        provider.setBaseUrl(defaultBaseUrl(request.providerType(), request.baseUrl()));
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            runtimeFactory.encryptApiKey(provider, request.apiKey());
        } else if (isCreate) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_REQUIRED", "API Key 不能为空");
        }
        provider.setChatModel(request.chatModel());
        provider.setEmbeddingModel(request.embeddingModel());
        provider.setEmbeddingDims(request.embeddingDims() != null ? request.embeddingDims() : 1536);
        provider.setSupportsChat(request.supportsChat() == null || request.supportsChat());
        provider.setSupportsEmbedding(request.supportsEmbedding() != null && request.supportsEmbedding());
        if (provider.getProviderType() == ProviderType.ANTHROPIC) {
            provider.setSupportsEmbedding(false);
        }
        if (request.enabled() != null) {
            provider.setEnabled(request.enabled());
        }
        if (request.timeoutMs() != null) {
            provider.setTimeoutMs(request.timeoutMs());
        }
        if (request.maxOutputTokens() != null) {
            provider.setMaxOutputTokens(Math.max(0, request.maxOutputTokens()));
        } else if (isCreate) {
            provider.setMaxOutputTokens(0);
        }
        if (request.contextWindow() != null) {
            provider.setContextWindow(Math.max(0, request.contextWindow()));
        } else if (isCreate) {
            provider.setContextWindow(0);
        }

        if (isCreate || request.reasoningEffort() != null || request.reasoningEnabled() != null) {
            ReasoningEffort effort = normalizeReasoningEffort(request.providerType(), request.reasoningEffort());
            boolean reasoningOn = request.reasoningEnabled() != null
                    ? request.reasoningEnabled() && effort.isEnabled()
                    : effort.isEnabled();
            if (!reasoningOn) {
                effort = ReasoningEffort.NONE;
            }
            provider.setReasoningEffort(effort);
            provider.setReasoningEnabled(reasoningOn);
        }
    }

    private void validateRequest(AiProviderRequest request, boolean isCreate) {
        if (request.providerType() == ProviderType.ANTHROPIC && Boolean.TRUE.equals(request.supportsEmbedding())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ANTHROPIC_NO_EMBEDDING", "Anthropic 不支持向量嵌入");
        }
        if (Boolean.TRUE.equals(request.supportsEmbedding()) && (request.embeddingModel() == null || request.embeddingModel().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMBEDDING_MODEL_REQUIRED", "启用嵌入时需指定 embedding 模型");
        }
        if (isCreate && (request.chatModel() == null || request.chatModel().isBlank())
                && (request.supportsChat() == null || request.supportsChat())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CHAT_MODEL_REQUIRED", "需指定对话模型");
        }
        if (request.maxOutputTokens() != null && request.maxOutputTokens() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MAX_TOKENS", "最大输出 Token 不能为负");
        }
        if (request.contextWindow() != null && request.contextWindow() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT_WINDOW", "上下文窗口不能为负");
        }
        // Normalize happens in applyRequest; reject only if caller insists on MAX for OpenAI without auto-normalize desire.
        // We auto-normalize MAX → HIGH for OpenAI-compat (OpsKat rule).
    }

    public static ReasoningEffort normalizeReasoningEffort(ProviderType type, ReasoningEffort requested) {
        ReasoningEffort effort = requested != null ? requested : ReasoningEffort.NONE;
        if (type == ProviderType.OPENAI_COMPAT && effort == ReasoningEffort.MAX) {
            return ReasoningEffort.HIGH;
        }
        return effort;
    }

    private AiProviderResponse toResponse(AiProvider provider) {
        var settings = settingsService.getSettings();
        return toResponse(provider, settings.getDefaultChatProviderId(), settings.getDefaultEmbeddingProviderId());
    }

    private AiProviderResponse toResponse(AiProvider provider, Long defaultChatId, Long defaultEmbeddingId) {
        return new AiProviderResponse(
                provider.getId(),
                provider.getName(),
                provider.getProviderType(),
                provider.getBaseUrl(),
                maskApiKey(provider),
                provider.getChatModel(),
                provider.getEmbeddingModel(),
                provider.getEmbeddingDims(),
                provider.isSupportsChat(),
                provider.isSupportsEmbedding(),
                provider.isEnabled(),
                provider.getTimeoutMs(),
                provider.getMaxOutputTokens(),
                provider.getContextWindow(),
                provider.isReasoningEnabled(),
                provider.getReasoningEffort() != null ? provider.getReasoningEffort() : ReasoningEffort.NONE,
                provider.getId().equals(defaultChatId),
                provider.getId().equals(defaultEmbeddingId),
                provider.getCreatedAt(),
                provider.getUpdatedAt());
    }

    private String maskApiKey(AiProvider provider) {
        if (provider.getApiKeyCipher() == null) {
            return null;
        }
        try {
            String key = runtimeFactory.decryptApiKey(provider);
            if (key.length() <= 8) {
                return "***";
            }
            return key.substring(0, 3) + "***" + key.substring(key.length() - 4);
        } catch (Exception ex) {
            return "***";
        }
    }

    private static String defaultBaseUrl(ProviderType type, String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return type == ProviderType.ANTHROPIC ? "https://api.anthropic.com" : "https://api.openai.com/v1";
    }

    private static String sanitizeError(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.replaceAll("(?i)(sk-|Bearer\\s+)\\S+", "$1***");
    }

    private void audit(Long actorId, String actorName, String action, Long providerId) {
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, action, "ai_provider:" + providerId, "LOW", "SUCCESS", "{}", null, null));
    }
}
