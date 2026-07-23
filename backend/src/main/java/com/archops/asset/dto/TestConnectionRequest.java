package com.archops.asset.dto;

import com.archops.asset.domain.SshAuthType;
import java.util.List;

/**
 * Test SSH connectivity either for a saved asset or with ephemeral form credentials.
 * Jump IDs refer to existing assets that already have SSH credentials (shared dialer).
 */
public record TestConnectionRequest(
        Long assetId,
        String host,
        Integer port,
        String username,
        SshAuthType authType,
        String secret,
        List<Long> jumpAssetIds) {}
