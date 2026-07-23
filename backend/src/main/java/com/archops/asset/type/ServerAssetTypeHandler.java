package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetRequest;
import org.springframework.stereotype.Component;

@Component
public class ServerAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.SERVER.name();
    }

    @Override
    public int defaultPort() {
        return 22;
    }

    @Override
    public String policyKind() {
        return "SSH";
    }

    @Override
    public void validateCreate(AssetRequest req) {
        validateCommon(req);
        requireHost(req);
    }

    @Override
    public void validateUpdate(AssetRequest req) {
        validateCommon(req);
        requireHost(req);
    }
}
