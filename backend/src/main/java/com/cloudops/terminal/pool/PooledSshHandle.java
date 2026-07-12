package com.cloudops.terminal.pool;

import org.apache.sshd.client.session.ClientSession;

/** Auto-closeable handle that releases a pooled SSH session reference. */
public final class PooledSshHandle implements AutoCloseable {

    private final ClientSession session;
    private final Long userId;
    private final Long assetId;
    private final SshConnectionPool pool;
    private boolean released;

    PooledSshHandle(ClientSession session, Long userId, Long assetId, SshConnectionPool pool) {
        this.session = session;
        this.userId = userId;
        this.assetId = assetId;
        this.pool = pool;
    }

    public ClientSession session() {
        return session;
    }

    @Override
    public void close() {
        if (!released) {
            released = true;
            pool.release(userId, assetId);
        }
    }
}
