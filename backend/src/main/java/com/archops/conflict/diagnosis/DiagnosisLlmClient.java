package com.archops.conflict.diagnosis;

import com.archops.common.ai.AiEgressConfig;
import com.archops.common.ai.AiEgressDeniedException;
import com.archops.common.ai.AiEgressProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional OpenAI-compatible chat completion for diagnosis summary enrichment.
 * Never carries business DB / order / customer / finance payloads.
 */
@Component
public class DiagnosisLlmClient {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisLlmClient.class);

    private final AiEgressProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public DiagnosisLlmClient(
            AiEgressProperties properties,
            @Qualifier(AiEgressConfig.AI_WEB_CLIENT_BUILDER) WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public Optional<String> enrichSummary(String ruleSummary, List<String> forkLabels) {
        if (!AiEgressConfig.isConfigured(properties)) {
            return Optional.empty();
        }
        try {
            String base = properties.getBaseUrl().replaceAll("/$", "");
            WebClient client = webClientBuilder.baseUrl(base).build();
            Map<String, Object> body = Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You rewrite ArchOps conflict diagnosis summaries. "
                                            + "Do not invent facts. Do not mention customer/order/finance data."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", "Rule summary: " + ruleSummary
                                            + "\nForks: " + String.join(", ", forkLabels)
                                            + "\nRewrite in one short Chinese paragraph."
                            )
                    ),
                    "temperature", 0.2
            );
            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(8));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(content.asText().trim());
        } catch (AiEgressDeniedException ex) {
            log.warn("AI egress denied: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("AI enrichment failed, falling back to rules: {}", ex.toString());
            return Optional.empty();
        }
    }
}
