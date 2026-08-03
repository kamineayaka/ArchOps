package com.archops.asset.type;

import com.archops.asset.domain.SshAuthType;
import java.util.List;

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
        String database,
        Long userId,
        List<String> roles) {

    public ConnectivityContext(
            Long assetId,
            String host,
            Integer port,
            String username,
            SshAuthType authType,
            String secret,
            String database) {
        this(assetId, host, port, username, authType, secret, database, null, List.of());
    }
}
