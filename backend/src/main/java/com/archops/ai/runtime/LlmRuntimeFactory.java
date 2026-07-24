package com.archops.ai.runtime;

import com.archops.ai.provider.domain.AiProvider;
import com.archops.ai.provider.domain.ProviderType;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LlmRuntimeFactory {

    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;

    public LlmRuntimeFactory(CredentialCipher credentialCipher, ObjectMapper objectMapper) {
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
    }

    public LlmRuntime createChatRuntime(AiProvider provider) {
        if (!provider.isEnabled() || !provider.isSupportsChat()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_NOT_CHAT", "Provider does not support chat");
        }
        String apiKey = decryptApiKey(provider);
        LlmGenerationConfig config = LlmGenerationConfig.from(provider);
        return switch (provider.getProviderType()) {
            case OPENAI_COMPAT -> new OpenAiCompatRuntime(
                    provider.getBaseUrl(), apiKey, provider.getChatModel(), provider.getTimeoutMs(),
                    config, objectMapper);
            case ANTHROPIC -> new AnthropicRuntime(
                    provider.getBaseUrl(), apiKey, provider.getChatModel(), provider.getTimeoutMs(),
                    config, objectMapper);
        };
    }

    public OpenAiCompatRuntime createEmbeddingRuntime(AiProvider provider) {
        if (!provider.isEnabled() || !provider.isSupportsEmbedding()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_NOT_EMBEDDING", "Provider does not support embedding");
        }
        if (provider.getProviderType() != ProviderType.OPENAI_COMPAT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EMBEDDING_PROVIDER", "Only OpenAI-compatible providers support embedding");
        }
        return new OpenAiCompatRuntime(
                provider.getBaseUrl(),
                decryptApiKey(provider),
                provider.getEmbeddingModel(),
                provider.getTimeoutMs(),
                LlmGenerationConfig.from(provider),
                objectMapper);
    }

    public String decryptApiKey(AiProvider provider) {
        if (provider.getApiKeyCipher() == null || provider.getApiKeyIv() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "API_KEY_MISSING", "Provider API key is not configured");
        }
        return credentialCipher.decrypt(provider.getApiKeyCipher(), provider.getApiKeyIv());
    }

    public void encryptApiKey(AiProvider provider, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        CredentialCipher.EncryptedSecret secret = credentialCipher.encrypt(apiKey);
        provider.setApiKeyCipher(secret.cipher());
        provider.setApiKeyIv(secret.iv());
    }
}
