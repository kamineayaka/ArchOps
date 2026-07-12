package com.cloudops.terminal.pool;

import java.time.Instant;

public record SshPoolEntryResponse(Long assetId, int refCount, Instant lastUsed, boolean alive) {}
