package com.cloudops.ai.provider.service;

import com.cloudops.ai.provider.domain.PlatformAiSettings;
import com.cloudops.ai.provider.dto.PlatformAiSettingsRequest;
import com.cloudops.ai.provider.dto.PlatformAiSettingsResponse;
import com.cloudops.ai.provider.repository.PlatformAiSettingsRepository;
import com.cloudops.audit.service.AuditService;
import com.cloudops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAiSettingsService {

    private final PlatformAiSettingsRepository repository;
    private final AuditService auditService;

    public PlatformAiSettingsService(PlatformAiSettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PlatformAiSettings getSettings() {
        return repository.findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("platform_ai_settings row missing"));
    }

    @Transactional(readOnly = true)
    public PlatformAiSettingsResponse toResponse() {
        PlatformAiSettings s = getSettings();
        return new PlatformAiSettingsResponse(
                s.getDefaultChatProviderId(),
                s.getDefaultEmbeddingProviderId(),
                s.isRagEnabled(),
                s.getRagTopK(),
                s.getRagMinSimilarity());
    }

    @Transactional
    public PlatformAiSettingsResponse update(PlatformAiSettingsRequest request, Long actorId, String actorName) {
        PlatformAiSettings settings = getSettings();
        if (request.defaultChatProviderId() != null) {
            settings.setDefaultChatProviderId(request.defaultChatProviderId());
        }
        if (request.defaultEmbeddingProviderId() != null) {
            settings.setDefaultEmbeddingProviderId(request.defaultEmbeddingProviderId());
        }
        if (request.ragEnabled() != null) {
            settings.setRagEnabled(request.ragEnabled());
        }
        if (request.ragTopK() != null) {
            settings.setRagTopK(request.ragTopK());
        }
        if (request.ragMinSimilarity() != null) {
            settings.setRagMinSimilarity(request.ragMinSimilarity());
        }
        repository.save(settings);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "ai.settings.update", "platform", "LOW", "SUCCESS", "{}", null, null));
        return toResponse();
    }

    @Transactional
    public void setDefaultChatProvider(Long providerId) {
        PlatformAiSettings settings = getSettings();
        settings.setDefaultChatProviderId(providerId);
        repository.save(settings);
    }

    @Transactional
    public void setDefaultEmbeddingProvider(Long providerId) {
        PlatformAiSettings settings = getSettings();
        settings.setDefaultEmbeddingProviderId(providerId);
        repository.save(settings);
    }

    public void requireProviderNotDefault(Long providerId) {
        PlatformAiSettings settings = getSettings();
        if (providerId.equals(settings.getDefaultChatProviderId())
                || providerId.equals(settings.getDefaultEmbeddingProviderId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "PROVIDER_IS_DEFAULT", "请先更换平台默认 Provider 后再删除");
        }
    }
}
