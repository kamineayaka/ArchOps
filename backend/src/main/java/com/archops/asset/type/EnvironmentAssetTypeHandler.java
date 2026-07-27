package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.ENVIRONMENT.name();
    }

    @Override
    public int defaultPort() {
        return 0;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }

    @Override
    public ConnectAction connectAction() {
        return ConnectAction.NONE;
    }
}
