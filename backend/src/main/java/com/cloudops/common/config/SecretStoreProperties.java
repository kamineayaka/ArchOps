package com.cloudops.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudops.secrets")
public record SecretStoreProperties(String path) {}
