package com.archops.knowledge.architecture;

import com.archops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Canonical Architecture partition keys: global / group:{id} / asset:{id}. */
public final class PartitionKeys {
    public static final String GLOBAL = "global";

    private PartitionKeys() {}

    public static String group(Long groupId) {
        requireId(groupId, "groupId");
        return "group:" + groupId;
    }

    public static String asset(Long assetId) {
        requireId(assetId, "assetId");
        return "asset:" + assetId;
    }

    public static void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PARTITION_KEY", "partitionKey 不能为空");
        }
        if (GLOBAL.equals(key)) {
            return;
        }
        if (key.startsWith("group:") || key.startsWith("asset:")) {
            String idPart = key.substring(key.indexOf(':') + 1);
            try {
                Long.parseLong(idPart);
                return;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PARTITION_KEY",
                "partitionKey 必须是 global / group:{id} / asset:{id}");
    }

    private static void requireId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ID", name + " 无效");
        }
    }
}
