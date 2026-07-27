package com.archops.graph.service;

import com.archops.common.exception.BusinessException;
import com.archops.graph.changeset.GraphChangeSet.GraphRef;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Resolves tempId / elementId / pgAssetId within a single merge. */
public final class GraphTempBinder {

    public record Binding(UUID elementId, Long pgAssetId) {}

    private final Map<String, Binding> byTempId = new HashMap<>();
    private final Map<UUID, Long> pgByElement = new HashMap<>();

    public void bind(String tempId, UUID elementId, Long pgAssetId) {
        if (tempId == null || tempId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TEMP_ID_REQUIRED", "tempId 不能为空");
        }
        if (elementId == null || pgAssetId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BIND_INCOMPLETE", "elementId/pgAssetId 不能为空");
        }
        byTempId.put(tempId, new Binding(elementId, pgAssetId));
        pgByElement.put(elementId, pgAssetId);
    }

    public void remember(UUID elementId, Long pgAssetId) {
        if (elementId != null && pgAssetId != null) {
            pgByElement.put(elementId, pgAssetId);
        }
    }

    public Binding requireTemp(String tempId) {
        Binding b = byTempId.get(tempId);
        if (b == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "TEMP_ID_UNKNOWN", "未知 tempId: " + tempId);
        }
        return b;
    }

    public UUID resolveElementId(GraphRef ref) {
        if (ref == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_REF_REQUIRED", "节点引用不能为空");
        }
        if (ref.tempId() != null && !ref.tempId().isBlank()) {
            return requireTemp(ref.tempId()).elementId();
        }
        if (ref.elementId() != null && !ref.elementId().isBlank()) {
            return UUID.fromString(ref.elementId().trim());
        }
        throw new BusinessException(
                HttpStatus.BAD_REQUEST, "GRAPH_REF_INVALID", "引用需要 elementId 或 tempId");
    }

    public Long pgAssetIdOf(UUID elementId) {
        return pgByElement.get(elementId);
    }
}
