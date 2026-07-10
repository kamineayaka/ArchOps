package com.cloudops.ai.provider.bootstrap;

import com.cloudops.ai.provider.domain.AiProvider;
import com.cloudops.ai.provider.domain.ProviderType;
import com.cloudops.ai.provider.repository.AiProviderRepository;
import com.cloudops.ai.provider.service.PlatformAiSettingsService;
import com.cloudops.ai.runtime.LlmRuntimeFactory;
import com.cloudops.common.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class AiProviderBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiProviderBootstrap.class);

    private final AiProviderRepository repository;
    private final PlatformAiSettingsService settingsService;
    private final LlmRuntimeFactory runtimeFactory;
    private final AiProperties aiProperties;

    public AiProviderBootstrap(
            AiProviderRepository repository,
            PlatformAiSettingsService settingsService,
            LlmRuntimeFactory runtimeFactory,
            AiProperties aiProperties) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.runtimeFactory = runtimeFactory;
        this.aiProperties = aiProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        String apiKey = aiProperties.openaiCompat().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("No AI providers configured and OPENAI_API_KEY is empty; configure providers in the admin UI");
            return;
        }
        AiProvider provider = new AiProvider();
        provider.setName("OpenAI (migrated from env)");
        provider.setProviderType(ProviderType.OPENAI_COMPAT);
        provider.setBaseUrl(aiProperties.openaiCompat().baseUrl());
        provider.setChatModel(aiProperties.openaiCompat().model());
        provider.setEmbeddingModel("text-embedding-3-small");
        provider.setEmbeddingDims(1536);
        provider.setSupportsChat(true);
        provider.setSupportsEmbedding(true);
        provider.setEnabled(true);
        provider.setTimeoutMs(aiProperties.openaiCompat().timeoutMs());
        runtimeFactory.encryptApiKey(provider, apiKey);
        provider = repository.save(provider);
        settingsService.setDefaultChatProvider(provider.getId());
        settingsService.setDefaultEmbeddingProvider(provider.getId());
        log.info("Seeded default AI provider from OPENAI_API_KEY (id={})", provider.getId());
    }
}
