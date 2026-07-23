package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import org.springframework.stereotype.Component;

@Component
public class ServiceAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.SERVICE.name();
    }

    @Override
    public int defaultPort() {
        return 80;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }
}
