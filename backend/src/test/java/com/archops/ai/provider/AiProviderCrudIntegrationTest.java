package com.archops.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archops.ai.provider.domain.PlatformAiSettings;
import com.archops.ai.provider.domain.ProviderType;
import com.archops.ai.provider.dto.AiProviderRequest;
import com.archops.ai.provider.dto.PlatformAiSettingsRequest;
import com.archops.ai.provider.repository.AiProviderRepository;
import com.archops.ai.provider.repository.PlatformAiSettingsRepository;
import com.archops.user.domain.User;
import com.archops.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AiProviderCrudIntegrationTest {

    private static final String API_KEY = "sk-test-key-12345";
    private static final String MASKED_API_KEY = "sk-***2345";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("archops")
            .withUsername("archops")
            .withPassword("archops");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static Path secretsPath;

    @BeforeAll
    static void initSecretsPath() throws IOException {
        Path dir = Files.createTempDirectory("archops-test-secrets");
        secretsPath = dir.resolve("secrets.properties");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("JWT_SECRET", () -> "dev-only-secret-change-in-production-must-be-at-least-256-bits!!");
        registry.add("CREDENTIALS_MASTER_KEY", () -> "credentials-test-master-key");
        registry.add("OPENAI_API_KEY", () -> "");
        registry.add("archops.secrets.path", () -> secretsPath.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiProviderRepository providerRepository;

    @Autowired
    private PlatformAiSettingsRepository settingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void resetDataAndLogin() throws Exception {
        userRepository.findByUsername("admin").ifPresent(user -> {
            user.setPassword(passwordEncoder.encode("admin123"));
            userRepository.save(user);
        });
        providerRepository.deleteAll();
        PlatformAiSettings settings = settingsRepository.findById((short) 1).orElseThrow();
        settings.setDefaultChatProviderId(null);
        settings.setDefaultEmbeddingProviderId(null);
        settingsRepository.save(settings);
        loginAsAdmin();
    }

    private void loginAsAdmin() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        adminToken = objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    @Test
    void createUpdateDeleteProviderFlow() throws Exception {
        JsonNode created = createProvider("openai-test", API_KEY);
        long providerId = created.path("id").asLong();

        assertThat(created.path("apiKeyMasked").asText()).isEqualTo(MASKED_API_KEY);
        assertThat(created.path("defaultChat").asBoolean()).isTrue();
        assertThat(created.path("chatModel").asText()).isEqualTo("gpt-4o-mini");

        JsonNode updated = updateProviderName(providerId, "openai-renamed");
        assertThat(updated.path("name").asText()).isEqualTo("openai-renamed");
        assertThat(updated.path("apiKeyMasked").asText()).isEqualTo(MASKED_API_KEY);

        mockMvc.perform(delete("/api/ai/providers/{id}", providerId).header("Authorization", bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVIDER_IS_DEFAULT"));

        long secondId = createProvider("openai-secondary", "sk-secondary-key-9999").path("id").asLong();

        mockMvc.perform(put("/api/ai/settings")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlatformAiSettingsRequest(secondId, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultChatProviderId").value(secondId));

        mockMvc.perform(delete("/api/ai/providers/{id}", providerId).header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ai/providers/all").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(secondId));
    }

    @Test
    void listProviders_masksApiKeyAndRespectsDefaults() throws Exception {
        createProvider("mask-test", API_KEY);

        mockMvc.perform(get("/api/ai/providers").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apiKeyMasked").value(MASKED_API_KEY))
                .andExpect(jsonPath("$.data[0].defaultChat").value(true));
    }

    private JsonNode createProvider(String name, String apiKey) throws Exception {
        AiProviderRequest request = new AiProviderRequest(
                name,
                ProviderType.OPENAI_COMPAT,
                null,
                apiKey,
                "gpt-4o-mini",
                null,
                null,
                true,
                false,
                true,
                null,
                null,
                null,
                null,
                null);
        String response = mockMvc.perform(post("/api/ai/providers")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode updateProviderName(long id, String name) throws Exception {
        AiProviderRequest request = new AiProviderRequest(
                name,
                ProviderType.OPENAI_COMPAT,
                null,
                null,
                "gpt-4o-mini",
                null,
                null,
                true,
                false,
                true,
                null,
                null,
                null,
                null,
                null);
        String response = mockMvc.perform(put("/api/ai/providers/{id}", id)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }
}
