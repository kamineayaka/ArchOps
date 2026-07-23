package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetRequest;
import org.springframework.stereotype.Component;

/**
 * Stub handler proving OCP: adding DATABASE requires only this component + frontend register,
 * not changes to {@code AssetService} or other shared switch statements.
 */
@Component
public class DatabaseAssetTypeHandler extends AbstractAssetTypeHandler {

    @Override
    public String type() {
        return AssetKind.DATABASE.name();
    }

    @Override
    public int defaultPort() {
        return 5432;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
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
