package com.cloudops.knowledge.indexing;

import com.cloudops.ai.provider.domain.AiProvider;
import com.cloudops.ai.provider.repository.AiProviderRepository;
import com.cloudops.ai.provider.service.PlatformAiSettingsService;
import com.cloudops.ai.runtime.LlmRuntimeFactory;
import com.cloudops.ai.runtime.OpenAiCompatRuntime;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderResolver {

    private final PlatformAiSettingsService settingsService;
    private final AiProviderRepository providerRepository;
    private final LlmRuntimeFactory runtimeFactory;

    public EmbeddingProviderResolver(
            PlatformAiSettingsService settingsService,
            AiProviderRepository providerRepository,
            LlmRuntimeFactory runtimeFactory) {
        this.settingsService = settingsService;
        this.providerRepository = providerRepository;
        this.runtimeFactory = runtimeFactory;
    }

    public EmbeddingProvider active() {
        Long id = settingsService.getSettings().getDefaultEmbeddingProviderId();
        if (id == null) {
            throw new EmbeddingException("未配置默认嵌入 Provider，请在 AI 设置中添加 OpenAI 兼容 Provider");
        }
        AiProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new EmbeddingException("嵌入 Provider 不存在: " + id));
        if (!provider.isEnabled() || !provider.isSupportsEmbedding()) {
            throw new EmbeddingException("嵌入 Provider 已禁用或不支持向量嵌入");
        }
        OpenAiCompatRuntime runtime = runtimeFactory.createEmbeddingRuntime(provider);
        return new DbEmbeddingProvider(provider, runtime);
    }
}
