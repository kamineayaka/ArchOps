package com.cloudops.terminal.pool;

import java.time.Instant;
import org.apache.sshd.client.session.ClientSession;

final class SshPoolEntry {

    private final ClientSession session;
    private final Long assetId;
    private int refCount;
    private Instant lastUsed;

    SshPoolEntry(ClientSession session, Long assetId) {
        this.session = session;
        this.assetId = assetId;
        this.refCount = 1;
        this.lastUsed = Instant.now();
    }

    synchronized ClientSession acquire() {
        refCount++;
        lastUsed = Instant.now();
        return session;
    }

    synchronized boolean release() {
        refCount--;
        lastUsed = Instant.now();
        return refCount <= 0;
    }

    synchronized int refCount() {
        return refCount;
    }

    synchronized Instant lastUsed() {
        return lastUsed;
    }

    synchronized boolean isIdle(long idleMillis) {
        return refCount <= 0 && Instant.now().toEpochMilli() - lastUsed.toEpochMilli() > idleMillis;
    }

    synchronized boolean isAlive() {
        return session.isOpen() && session.isAuthenticated();
    }

    void close() {
        try {
            if (session.isOpen()) {
                session.close(true);
            }
        } catch (Exception ignored) {
        }
    }

    Long assetId() {
        return assetId;
    }

    ClientSession session() {
        return session;
    }
}
