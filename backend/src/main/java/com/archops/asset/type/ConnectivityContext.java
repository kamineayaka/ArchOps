package com.archops.asset.type;

import com.archops.asset.domain.SshAuthType;
import java.util.List;

/**
 * Inputs for a type-owned connectivity probe. Built by {@code AssetConnectionTestService}
 * from ephemeral form fields or a saved asset + credential.
 */
public record ConnectivityContext(
        Long assetId,
        String host,
        Integer port,
        String username,
        SshAuthType authType,
        String secret,
        List<Long> jumpAssetIds,
        /** Optional logical database / schema name (DATABASE assets). */
        String database,
        /** K8S: API_SERVER or JUMP_KUBECTL. */
        String k8sMode,
        /** K8S API server URL when mode is API_SERVER. */
        String apiServerUrl) {

    public List<Long> jumpsOrEmpty() {
        return jumpAssetIds != null ? jumpAssetIds : List.of();
    }
}
