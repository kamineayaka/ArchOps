package com.cloudops.terminal.pool;

import com.cloudops.common.config.SshPoolProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Server-side SSH connection pool keyed by (userId, assetId).
 * Inspired by OpsKat {@code internal/sshpool/pool.go}.
 */
@Service
public class SshConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(SshConnectionPool.class);

    private final AssetSshDialer dialer;
    private final SshPoolProperties properties;
    private final ConcurrentMap<SshPoolKey, SshPoolEntry> entries = new ConcurrentHashMap<>();

    public SshConnectionPool(AssetSshDialer dialer, SshPoolProperties properties, SshPoolCleanupScheduler cleanupScheduler) {
        this.dialer = dialer;
        this.properties = properties;
        cleanupScheduler.register(this);
    }

    public PooledSshHandle acquire(Long userId, Long assetId) throws Exception {
        SshPoolKey key = new SshPoolKey(userId, assetId);
        SshPoolEntry entry = entries.get(key);

        if (entry != null && entry.isAlive()) {
            entry.acquire();
            return new PooledSshHandle(entry.session(), userId, assetId, this);
        }
        if (entry != null) {
            remove(userId, assetId);
        }

        ClientSession session = dialer.dial(assetId);
        SshPoolEntry created = new SshPoolEntry(session, assetId);

        SshPoolEntry existing = entries.putIfAbsent(key, created);
        if (existing != null) {
            created.close();
            if (existing.isAlive()) {
                existing.acquire();
                return new PooledSshHandle(existing.session(), userId, assetId, this);
            }
            remove(userId, assetId);
            return acquire(userId, assetId);
        }

        log.info("SSH pool: opened connection user={} asset={}", userId, assetId);
        return new PooledSshHandle(session, userId, assetId, this);
    }

    public void warm(Long userId, Long assetId) throws Exception {
        try (PooledSshHandle ignored = acquire(userId, assetId)) {
            log.debug("SSH pool warmed user={} asset={}", userId, assetId);
        }
    }

    void release(Long userId, Long assetId) {
        SshPoolKey key = new SshPoolKey(userId, assetId);
        SshPoolEntry entry = entries.get(key);
        if (entry == null) {
            return;
        }
        boolean idle = entry.release();
        if (idle && entry.isIdle(0)) {
            log.debug("SSH pool: released to idle user={} asset={}", userId, assetId);
        }
    }

    public void remove(Long userId, Long assetId) {
        SshPoolKey key = new SshPoolKey(userId, assetId);
        SshPoolEntry entry = entries.remove(key);
        if (entry != null) {
            entry.close();
            log.info("SSH pool: evicted user={} asset={}", userId, assetId);
        }
    }

    public List<SshPoolEntryResponse> listForUser(Long userId) {
        List<SshPoolEntryResponse> result = new ArrayList<>();
        for (var e : entries.entrySet()) {
            if (!e.getKey().userId().equals(userId)) {
                continue;
            }
            SshPoolEntry entry = e.getValue();
            result.add(new SshPoolEntryResponse(
                    e.getKey().assetId(),
                    entry.refCount(),
                    entry.lastUsed(),
                    entry.isAlive()));
        }
        return result;
    }

    void cleanupIdle() {
        long idleMillis = properties.idleTimeout().toMillis();
        List<SshPoolKey> toRemove = new ArrayList<>();
        for (var e : entries.entrySet()) {
            if (e.getValue().isIdle(idleMillis)) {
                toRemove.add(e.getKey());
            }
        }
        for (SshPoolKey key : toRemove) {
            SshPoolEntry removed = entries.remove(key);
            if (removed != null) {
                removed.close();
                log.info("SSH pool: idle evicted user={} asset={}", key.userId(), key.assetId());
            }
        }
    }
}
