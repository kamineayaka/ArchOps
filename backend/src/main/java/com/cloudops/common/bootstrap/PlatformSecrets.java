package com.cloudops.common.bootstrap;

/**
 * Resolved platform secrets after env / file / auto-generation bootstrap.
 */
public record PlatformSecrets(String jwtSecret, String credentialsMasterKey) {}
