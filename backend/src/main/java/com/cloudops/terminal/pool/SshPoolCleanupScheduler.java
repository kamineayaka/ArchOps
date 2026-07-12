package com.cloudops.terminal.pool;

import com.cloudops.common.config.SshPoolProperties;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SshPoolCleanupScheduler {

    private final SshPoolProperties properties;
    private final AtomicReference<SshConnectionPool> poolRef = new AtomicReference<>();

    public SshPoolCleanupScheduler(SshPoolProperties properties) {
        this.properties = properties;
    }

    void register(SshConnectionPool pool) {
        poolRef.set(pool);
    }

    @Scheduled(fixedDelay = 30_000)
    void cleanupIdle() {
        SshConnectionPool pool = poolRef.get();
        if (pool != null) {
            pool.cleanupIdle();
        }
    }
}
