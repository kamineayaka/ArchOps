package com.archops.observed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "archops.observation")
public class ObservationProperties {

    /**
     * Heartbeats older than this make sourced observed facts 观测空洞.
     */
    private Duration heartbeatTimeout = Duration.ofMinutes(5);

    private long hollowScanIntervalMs = 5000L;

    public Duration getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(Duration heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public long getHollowScanIntervalMs() {
        return hollowScanIntervalMs;
    }

    public void setHollowScanIntervalMs(long hollowScanIntervalMs) {
        this.hollowScanIntervalMs = hollowScanIntervalMs;
    }
}
