package com.archops.asset.type;

import com.archops.asset.domain.SshAuthType;

/**
 * Inputs for a type-owned connectivity probe. Built from a saved asset + credential.
 */
public record ConnectivityContext(
        Long assetId,
        String host,
        Integer port,
        String username,
        SshAuthType authType,
        String secret,
        /** Optional logical database / schema name (DATABASE assets). */
        String database) {}
