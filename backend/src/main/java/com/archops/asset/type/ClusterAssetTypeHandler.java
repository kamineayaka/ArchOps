package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import org.springframework.stereotype.Component;

@Component
public class ClusterAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.CLUSTER.name();
    }

    @Override
    public int defaultPort() {
        return 6443;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }
}
