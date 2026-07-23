package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import org.springframework.stereotype.Component;

@Component
public class NetworkAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.NETWORK.name();
    }

    @Override
    public int defaultPort() {
        return 0;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }
}
