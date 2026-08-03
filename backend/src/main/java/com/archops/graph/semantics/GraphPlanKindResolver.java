package com.archops.graph.semantics;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.repository.AssetRepository;
import com.archops.graph.domain.GraphRelType;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves endpoint kinds for plan-time REL_CREATE validation
 * (existing PG anchors + NODE_CREATE ops in the same plan).
 */
@Component
public class GraphPlanKindResolver {

    private final AssetRepository assetRepository;

    public GraphPlanKindResolver(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public Map<String, AssetKind> indexKinds(List<Map<String, Object>> ops) {
        Map<String, AssetKind> byKey = new HashMap<>();
        for (Asset asset : assetRepository.findByDeletedAtIsNull()) {
            if (asset.getElementId() != null) {
                byKey.put(asset.getElementId().toString().toLowerCase(Locale.ROOT), asset.getKind());
            }
            if (asset.getId() != null) {
                byKey.put("pg:" + asset.getId(), asset.getKind());
            }
        }
        if (ops == null) {
            return byKey;
        }
        for (Map<String, Object> op : ops) {
            if (!"NODE_CREATE".equalsIgnoreCase(str(op.get("op")))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> props = op.get("properties") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m
                    : Map.of();
            AssetKind kind = GraphRelEndpointRules.parseKind(str(props.get("kind")));
            if (kind == null) {
                continue;
            }
            String elementId = str(props.get("elementId"));
            if (elementId != null) {
                byKey.put(elementId.toLowerCase(Locale.ROOT), kind);
            }
            String tempId = str(op.get("tempId"));
            if (tempId != null) {
                byKey.put("temp:" + tempId.toLowerCase(Locale.ROOT), kind);
            }
        }
        return byKey;
    }

    public AssetKind resolve(Map<String, AssetKind> index, Object refObj) {
        if (!(refObj instanceof Map<?, ?> ref)) {
            return null;
        }
        Object elementId = ref.get("elementId");
        if (elementId != null && !String.valueOf(elementId).isBlank()) {
            String key = String.valueOf(elementId).trim().toLowerCase(Locale.ROOT);
            AssetKind kind = index.get(key);
            if (kind != null) {
                return kind;
            }
            try {
                return assetRepository
                        .findByElementIdAndDeletedAtIsNull(UUID.fromString(String.valueOf(elementId)))
                        .map(Asset::getKind)
                        .orElse(null);
            } catch (Exception ignored) {
                return null;
            }
        }
        Object pgAssetId = ref.get("pgAssetId");
        if (pgAssetId instanceof Number n) {
            AssetKind kind = index.get("pg:" + n.longValue());
            if (kind != null) {
                return kind;
            }
            return assetRepository.findById(n.longValue()).map(Asset::getKind).orElse(null);
        }
        Object tempId = ref.get("tempId");
        if (tempId != null && !String.valueOf(tempId).isBlank()) {
            return index.get("temp:" + String.valueOf(tempId).trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    public void validateRelCreate(Map<String, AssetKind> index, Map<String, Object> op) {
        GraphRelType type = GraphRelType.from(str(op.get("type")));
        AssetKind from = resolve(index, op.get("from"));
        AssetKind to = resolve(index, op.get("to"));
        GraphRelEndpointRules.validate(type, from, to);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
