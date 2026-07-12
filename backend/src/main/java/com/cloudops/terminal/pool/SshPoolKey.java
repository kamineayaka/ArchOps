package com.cloudops.terminal.pool;

/**
 * Pool entries are scoped per CloudOps user and asset so sessions are not shared
 * across operators even when targeting the same machine.
 */
public record SshPoolKey(Long userId, Long assetId) {}
