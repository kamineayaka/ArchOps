package com.archops.asset.dto;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.SshAuthType;
import java.util.List;

/**
 * Test connectivity for a saved asset or ephemeral form credentials.
 * {@code kind} is required for ephemeral probes so the type handler can be resolved
 * without a shared {@code switch(kind)}.
 */
public record TestConnectionRequest(
        Long assetId,
        AssetKind kind,
        String host,
        Integer port,
        String username,
        SshAuthType authType,
        String secret,
        List<Long> jumpAssetIds,
        /** Optional database / schema name for DATABASE probes. */
        String database) {}
