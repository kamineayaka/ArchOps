package com.cloudops.common.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecretBootstrapConfig {

    @Bean
    PlatformSecrets platformSecrets(PlatformSecretStore secretStore) {
        return secretStore.secrets();
    }
}
