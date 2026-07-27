package com.archops.asset.type;

import com.archops.asset.domain.Asset;
import java.util.LinkedHashMap;
import java.util.Map;

abstract class AbstractAssetTypeHandler implements AssetTypeHandler {

    @Override
    public ConnectAction connectAction() {
        return ConnectAction.NONE;
    }

    @Override
    public Map<String, Object> safeView(Asset asset) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", asset.getId());
        view.put("elementId", asset.getElementId());
        view.put("name", asset.getName());
        view.put("kind", asset.getKind() != null ? asset.getKind().name() : type());
        view.put("host", asset.getHost());
        view.put("port", asset.getPort());
        view.put("metadata", asset.getMetadata());
        view.put("enabled", asset.isEnabled());
        return view;
    }
}
