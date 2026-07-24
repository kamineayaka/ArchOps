package com.archops.asset.type;

import com.archops.asset.domain.Asset;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.TestConnectionResponse;
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

    /** Primary workbench connect action for this kind. */
    ConnectAction connectAction();

    /** Sanitized view for APIs/UI — never include secrets. */
    Map<String, Object> safeView(Asset asset);

    void validateCreate(AssetRequest req);

    void validateUpdate(AssetRequest req);

    /**
     * Type-owned connectivity probe (SSH, TCP, JDBC, …).
     * Default: not supported — callers should surface a friendly message.
     */
    default TestConnectionResponse testConnection(ConnectivityContext ctx) {
        return new TestConnectionResponse(false, 0L, "该资产类型暂不支持测试连接");
    }
}
