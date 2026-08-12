package com.archops.common.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AiEgressProperties.class)
public class AiEgressConfig {

    public static final String AI_WEB_CLIENT_BUILDER = "aiWebClientBuilder";

    @Bean(AI_WEB_CLIENT_BUILDER)
    WebClient.Builder aiWebClientBuilder(AiEgressProperties properties) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .filter(allowlistFilter(properties));
    }

    private static ExchangeFilterFunction allowlistFilter(AiEgressProperties properties) {
        return (request, next) -> {
            URI uri = request.url();
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return reactor.core.publisher.Mono.error(new AiEgressDeniedException(
                        "AI egress requires HTTPS: " + uri));
            }
            String host = uri.getHost();
            if (host == null || properties.getAllowlistHosts().stream().noneMatch(host::equalsIgnoreCase)) {
                return reactor.core.publisher.Mono.error(new AiEgressDeniedException(
                        "AI egress host not allowlisted: " + host));
            }
            ClientRequest filtered = ClientRequest.from(request).build();
            return next.exchange(filtered);
        };
    }

    public static boolean isConfigured(AiEgressProperties properties) {
        return properties.isEnabled()
                && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }
}
