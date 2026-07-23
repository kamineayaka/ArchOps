package com.archops.asset.type;

import com.archops.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Collects {@link AssetTypeHandler} beans and indexes them by {@link AssetTypeHandler#type()}.
 */
@Component
public class AssetTypeRegistry {

    private final Map<String, AssetTypeHandler> handlers = new LinkedHashMap<>();

    public AssetTypeRegistry(List<AssetTypeHandler> registered) {
        for (AssetTypeHandler handler : registered) {
            String type = handler.type();
            if (handlers.containsKey(type)) {
                throw new IllegalStateException("Duplicate AssetTypeHandler for type: " + type);
            }
            handlers.put(type, handler);
        }
    }

    public List<AssetTypeHandler> all() {
        return List.copyOf(handlers.values());
    }

    public List<AssetTypeDescriptor> descriptors() {
        return handlers.values().stream().map(AssetTypeDescriptor::from).toList();
    }

    public Optional<AssetTypeHandler> find(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    public AssetTypeHandler findRequired(String type) {
        AssetTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "UNKNOWN_ASSET_TYPE", "未知资产类型: " + type);
        }
        return handler;
    }
}
