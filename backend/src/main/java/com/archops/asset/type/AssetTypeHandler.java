package com.archops.asset.type;

import com.archops.asset.domain.Asset;
import com.archops.asset.dto.AssetRequest;
import java.util.Map;

/**
 * SPI for asset kinds. Each kind self-registers as a Spring {@code @Component};
 * shared services must look up handlers via {@link AssetTypeRegistry} instead of
 * {@code switch (kind)}.
 */
public interface AssetTypeHandler {

    /** Matches {@link com.archops.asset.domain.AssetKind#name()}. */
    String type();

    int defaultPort();

    /** Policy family for risk / approval routing, e.g. {@code SSH} or {@code GENERIC}. */
    String policyKind();

    /** Sanitized view for APIs/UI — never include secrets. */
    Map<String, Object> safeView(Asset asset);

    void validateCreate(AssetRequest req);

    void validateUpdate(AssetRequest req);
}
