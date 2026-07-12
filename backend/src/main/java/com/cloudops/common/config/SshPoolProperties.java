package com.cloudops.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudops.ssh-pool")
public class SshPoolProperties {

    /** Idle timeout before evicting unused pooled connections. */
    private Duration idleTimeout = Duration.ofMinutes(5);

    /** Background cleanup interval. */
    private Duration cleanupInterval = Duration.ofSeconds(30);

    /** SSH connect/auth timeout. */
    private Duration connectTimeout = Duration.ofSeconds(15);

    public Duration idleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Duration cleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
