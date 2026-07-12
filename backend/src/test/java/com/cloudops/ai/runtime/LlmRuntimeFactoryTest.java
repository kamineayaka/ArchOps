package com.cloudops.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudops.ai.llm.LlmProvider.ChatMessage;
import com.cloudops.ai.llm.LlmProvider.ToolDefinition;
import com.cloudops.ai.provider.domain.AiProvider;
import com.cloudops.ai.provider.domain.ProviderType;
import com.cloudops.common.bootstrap.PlatformSecrets;
import com.cloudops.common.exception.BusinessException;
import com.cloudops.common.security.CredentialCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmRuntimeFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CredentialCipher credentialCipher;
    private LlmRuntimeFactory factory;

    @BeforeEach
    void setUp() {
        credentialCipher = new CredentialCipher(new PlatformSecrets("jwt-test", "credentials-test-master-key"));
        factory = new LlmRuntimeFactory(credentialCipher, objectMapper);
    }

    @Test
    void createChatRuntime_openAiCompat() {
        AiProvider provider = providerWithApiKey(openAiProvider(), "sk-openai");

        LlmRuntime runtime = factory.createChatRuntime(provider);

        assertThat(runtime).isInstanceOf(OpenAiCompatRuntime.class);
    }

    @Test
    void createChatRuntime_anthropic() {
        AiProvider provider = providerWithApiKey(anthropicProvider(), "sk-anthropic");

        LlmRuntime runtime = factory.createChatRuntime(provider);

        assertThat(runtime).isInstanceOf(AnthropicRuntime.class);
    }

    @Test
    void createChatRuntime_rejectsDisabledProvider() {
        AiProvider provider = providerWithApiKey(openAiProvider(), "sk-openai");
        provider.setEnabled(false);

        assertThatThrownBy(() -> factory.createChatRuntime(provider))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_CHAT");
    }

    @Test
    void createEmbeddingRuntime_openAiCompat() {
        AiProvider provider = providerWithApiKey(openAiProvider(), "sk-openai");
        provider.setSupportsEmbedding(true);
        provider.setEmbeddingModel("text-embedding-3-small");

        OpenAiCompatRuntime runtime = factory.createEmbeddingRuntime(provider);

        assertThat(runtime).isNotNull();
    }

    @Test
    void createEmbeddingRuntime_rejectsAnthropic() {
        AiProvider provider = providerWithApiKey(anthropicProvider(), "sk-anthropic");
        provider.setSupportsEmbedding(true);

        assertThatThrownBy(() -> factory.createEmbeddingRuntime(provider))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_EMBEDDING_PROVIDER");
    }

    @Test
    void encryptAndDecryptApiKey_roundTrip() {
        AiProvider provider = openAiProvider();

        factory.encryptApiKey(provider, "plain-key");

        assertThat(provider.getApiKeyCipher()).isNotNull();
        assertThat(provider.getApiKeyIv()).isNotNull();
        assertThat(factory.decryptApiKey(provider)).isEqualTo("plain-key");
    }

    @Test
    void encryptApiKey_skipsBlank() {
        AiProvider provider = openAiProvider();
        factory.encryptApiKey(provider, "existing");
        byte[] cipher = provider.getApiKeyCipher();
        byte[] iv = provider.getApiKeyIv();

        factory.encryptApiKey(provider, "  ");

        assertThat(provider.getApiKeyCipher()).isEqualTo(cipher);
        assertThat(provider.getApiKeyIv()).isEqualTo(iv);
    }

    @Test
    void decryptApiKey_requiresStoredSecret() {
        AiProvider provider = openAiProvider();
        provider.setApiKeyCipher(null);

        assertThatThrownBy(() -> factory.decryptApiKey(provider))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "API_KEY_MISSING");
    }

    @Test
    void openAiRequestBody_includesModelMessagesAndTools() throws Exception {
        OpenAiCompatRuntime runtime =
                (OpenAiCompatRuntime) factory.createChatRuntime(providerWithApiKey(openAiProvider(), "sk-openai"));

        List<ChatMessage> messages = List.of(ChatMessage.user("hello"));
        List<ToolDefinition> tools = List.of(
                new ToolDefinition("list_assets", "List assets", "{\"type\":\"object\"}"));
        String body = invokeBuildRequestBody(runtime, messages, tools, false);

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(root.path("stream").asBoolean()).isFalse();
        assertThat(root.path("messages")).hasSize(1);
        assertThat(root.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("tools")).hasSize(1);
        assertThat(root.path("tools").get(0).path("function").path("name").asText())
                .isEqualTo("list_assets");
    }

    @Test
    void anthropicRequestBody_extractsSystemAndTools() throws Exception {
        AnthropicRuntime runtime = (AnthropicRuntime)
                factory.createChatRuntime(providerWithApiKey(anthropicProvider(), "sk-anthropic"));

        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are helpful"), ChatMessage.user("ping"));
        List<ToolDefinition> tools = List.of(
                new ToolDefinition("ssh_exec", "Run SSH", "{\"type\":\"object\"}"));
        String body = invokeBuildRequestBody(runtime, messages, tools, false);

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.path("model").asText()).isEqualTo("claude-3-5-sonnet-20241022");
        assertThat(root.path("system").asText()).isEqualTo("You are helpful");
        assertThat(root.path("messages")).hasSize(1);
        assertThat(root.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("tools")).hasSize(1);
        assertThat(root.path("tools").get(0).path("name").asText()).isEqualTo("ssh_exec");
    }

    private AiProvider providerWithApiKey(AiProvider provider, String apiKey) {
        factory.encryptApiKey(provider, apiKey);
        return provider;
    }

    private static AiProvider openAiProvider() {
        AiProvider provider = new AiProvider();
        provider.setName("openai");
        provider.setProviderType(ProviderType.OPENAI_COMPAT);
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setChatModel("gpt-4o-mini");
        provider.setSupportsChat(true);
        provider.setSupportsEmbedding(false);
        provider.setEnabled(true);
        provider.setTimeoutMs(30_000);
        return provider;
    }

    private static AiProvider anthropicProvider() {
        AiProvider provider = new AiProvider();
        provider.setName("anthropic");
        provider.setProviderType(ProviderType.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        provider.setChatModel("claude-3-5-sonnet-20241022");
        provider.setSupportsChat(true);
        provider.setSupportsEmbedding(false);
        provider.setEnabled(true);
        provider.setTimeoutMs(30_000);
        return provider;
    }

    private String invokeBuildRequestBody(
            Object runtime, List<ChatMessage> messages, List<ToolDefinition> tools, boolean stream)
            throws Exception {
        Method method = runtime.getClass()
                .getDeclaredMethod("buildRequestBody", List.class, List.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(runtime, messages, tools, stream);
    }
}
