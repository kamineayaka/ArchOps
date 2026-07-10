package com.cloudops.ai.runtime;

import com.cloudops.ai.provider.domain.AiProvider;
import com.cloudops.ai.provider.repository.AiProviderRepository;
import com.cloudops.ai.provider.service.PlatformAiSettingsService;
import com.cloudops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LlmRuntimeResolver {

    private final AiProviderRepository providerRepository;
    private final PlatformAiSettingsService settingsService;
    private final LlmRuntimeFactory runtimeFactory;

    public LlmRuntimeResolver(
            AiProviderRepository providerRepository,
            PlatformAiSettingsService settingsService,
            LlmRuntimeFactory runtimeFactory) {
        this.providerRepository = providerRepository;
        this.settingsService = settingsService;
        this.runtimeFactory = runtimeFactory;
    }

    public ResolvedRuntime resolve(Long providerId) {
        AiProvider provider = resolveProvider(providerId);
        return new ResolvedRuntime(provider.getId(), provider.getName(), runtimeFactory.createChatRuntime(provider));
    }

    public AiProvider resolveProvider(Long providerId) {
        Long id = providerId != null ? providerId : settingsService.getSettings().getDefaultChatProviderId();
        if (id == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "NO_LLM_PROVIDER", "未配置 AI Provider，请在系统设置中添加");
        }
        AiProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "AI Provider 不存在"));
        if (!provider.isEnabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_DISABLED", "AI Provider 已禁用");
        }
        return provider;
    }

    public record ResolvedRuntime(Long providerId, String providerName, LlmRuntime runtime) {}
}
