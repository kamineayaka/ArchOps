package com.archops.knowledge.architecture;

import com.archops.common.exception.BusinessException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Architecture / graph scope keys stored in {@code partition_key}.
 *
 * <p>Canonical forms: {@code graph:global}, {@code cluster:{elementId}}, {@code tag:{slug}},
 * {@code view:{id}}, {@code asset:{elementId}}. Legacy {@code global}, {@code group:{id}},
 * {@code asset:{numericId}} remain valid during migration.
 */
public final class PartitionKeys {
    public static final String GLOBAL = "graph:global";
    public static final String LEGACY_GLOBAL = "global";

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,127}$");
    private static final Pattern UUID_RE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private PartitionKeys() {}

    public static String asset(Long assetId) {
        requireId(assetId, "assetId");
        return "asset:" + assetId;
    }

    public static String assetElement(UUID elementId) {
        if (elementId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ID", "elementId 无效");
        }
        return "asset:" + elementId;
    }

    public static String cluster(UUID elementId) {
        if (elementId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ID", "elementId 无效");
        }
        return "cluster:" + elementId;
    }

    public static String tag(String slug) {
        String normalized = normalizeSlug(slug);
        return "tag:" + normalized;
    }

    public static String view(Long viewId) {
        requireId(viewId, "viewId");
        return "view:" + viewId;
    }

    public static boolean isGlobal(String key) {
        String n = normalize(key);
        return GLOBAL.equals(n);
    }

    /** Normalize legacy {@code global} → {@code graph:global}. */
    public static String normalize(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        if (LEGACY_GLOBAL.equals(trimmed)) {
            return GLOBAL;
        }
        return trimmed;
    }

    public static void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PARTITION_KEY", "partitionKey 不能为空");
        }
        String k = normalize(key);
        if (GLOBAL.equals(k)) {
            return;
        }
        if (k.startsWith("cluster:")) {
            requireUuidOrNumeric(k.substring("cluster:".length()), "cluster");
            return;
        }
        if (k.startsWith("tag:")) {
            String slug = k.substring("tag:".length());
            if (!SLUG.matcher(slug).matches()) {
                throw invalid("partitionKey tag slug 无效");
            }
            return;
        }
        if (k.startsWith("view:")) {
            requireNumeric(k.substring("view:".length()), "view");
            return;
        }
        if (k.startsWith("asset:")) {
            requireUuidOrNumeric(k.substring("asset:".length()), "asset");
            return;
        }
        if (k.startsWith("group:")) {
            requireNumeric(k.substring("group:".length()), "group");
            return;
        }
        throw invalid("partitionKey 必须是 graph:global / cluster:{id} / tag:{slug} / view:{id} / asset:{id}");
    }

    public static String scopeKindOf(String key) {
        String k = normalize(key);
        if (isGlobal(k)) {
            return "graph";
        }
        int colon = k.indexOf(':');
        if (colon <= 0) {
            return "graph";
        }
        String prefix = k.substring(0, colon);
        if ("group".equals(prefix)) {
            return "tag";
        }
        return prefix;
    }

    public static String scopeRefOf(String key) {
        String k = normalize(key);
        if (isGlobal(k)) {
            return null;
        }
        int colon = k.indexOf(':');
        if (colon < 0 || colon == k.length() - 1) {
            return null;
        }
        return k.substring(colon + 1);
    }

    public static String normalizeSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "tag slug 不能为空");
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isEmpty() || !SLUG.matcher(slug).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "tag slug 无效");
        }
        return slug;
    }

    private static void requireUuidOrNumeric(String part, String label) {
        if (UUID_RE.matcher(part).matches()) {
            return;
        }
        requireNumeric(part, label);
    }

    private static void requireNumeric(String part, String label) {
        try {
            long id = Long.parseLong(part);
            if (id <= 0) {
                throw invalid(label + " id 无效");
            }
        } catch (NumberFormatException e) {
            throw invalid(label + " id 无效");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PARTITION_KEY", message);
    }

    private static void requireId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ID", name + " 无效");
        }
    }
}
