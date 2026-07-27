package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import org.springframework.stereotype.Component;

@Component
public class TagAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.TAG.name();
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
