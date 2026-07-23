package com.archops.asset.type;

import com.archops.asset.domain.Asset;
import com.archops.asset.dto.AssetRequest;
import com.archops.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

abstract class AbstractAssetTypeHandler implements AssetTypeHandler {

    @Override
    public Map<String, Object> safeView(Asset asset) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", asset.getId());
        view.put("name", asset.getName());
        view.put("kind", asset.getKind() != null ? asset.getKind().name() : type());
        view.put("host", asset.getHost());
        view.put("port", asset.getPort());
        view.put("metadata", asset.getMetadata());
        view.put("parentId", asset.getParentId());
        view.put("enabled", asset.isEnabled());
        return view;
    }

    @Override
    public void validateCreate(AssetRequest req) {
        validateCommon(req);
    }

    @Override
    public void validateUpdate(AssetRequest req) {
        validateCommon(req);
    }

    protected void validateCommon(AssetRequest req) {
        validatePort(req.port());
    }

    protected void validatePort(Integer port) {
        if (port != null && (port < 1 || port > 65535)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSET_PORT_INVALID", "端口必须在 1–65535 之间");
        }
    }

    protected void requireHost(AssetRequest req) {
        if (!StringUtils.hasText(req.host())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSET_HOST_REQUIRED", "主机地址不能为空");
        }
    }
}
