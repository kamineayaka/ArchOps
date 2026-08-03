package com.archops.graph.semantics;

import com.archops.asset.domain.AssetKind;
import com.archops.common.exception.BusinessException;
import com.archops.graph.domain.GraphRelType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Closed endpoint-kind rules for first-wave relationship types.
 * CONNECTS_VIA uses semantic B: target SERVER -[:CONNECTS_VIA]-> jump SERVER.
 */
public final class GraphRelEndpointRules {

    private static final Set<AssetKind> RUNS_ON_FROM = EnumSet.of(
            AssetKind.SERVICE, AssetKind.DATABASE, AssetKind.NETWORK);
    private static final Set<AssetKind> RUNS_ON_TO = EnumSet.of(AssetKind.SERVER, AssetKind.CLUSTER);
    private static final Set<AssetKind> LOGICAL = EnumSet.of(AssetKind.TAG, AssetKind.ENVIRONMENT);

    private GraphRelEndpointRules() {}

    public static void validate(GraphRelType type, AssetKind fromKind, AssetKind toKind) {
        if (fromKind == null || toKind == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "GRAPH_REL_ENDPOINT_KIND_UNKNOWN",
                    "无法解析边端点 kind（from=" + fromKind + ", to=" + toKind + "）");
        }
        switch (type) {
            case MEMBER_OF -> {
                require(toKind == AssetKind.CLUSTER, type, fromKind, toKind, "to 必须为 CLUSTER");
                require(fromKind != AssetKind.TAG, type, fromKind, toKind, "TAG 请使用 HAS_TAG，不要 MEMBER_OF");
            }
            case HAS_TAG -> require(toKind == AssetKind.TAG, type, fromKind, toKind, "to 必须为 TAG");
            case CONNECTS_VIA -> {
                require(fromKind == AssetKind.SERVER, type, fromKind, toKind, "from（目标机）必须为 SERVER");
                require(toKind == AssetKind.SERVER, type, fromKind, toKind, "to（跳板）必须为 SERVER");
            }
            case RUNS_ON -> {
                require(RUNS_ON_FROM.contains(fromKind), type, fromKind, toKind,
                        "from 须为 SERVICE / DATABASE / NETWORK");
                require(RUNS_ON_TO.contains(toKind), type, fromKind, toKind, "to 须为 SERVER / CLUSTER");
            }
            case DEPENDS_ON -> {
                require(!LOGICAL.contains(fromKind), type, fromKind, toKind, "from 不能为 TAG/ENVIRONMENT");
                require(!LOGICAL.contains(toKind), type, fromKind, toKind, "to 不能为 TAG/ENVIRONMENT");
            }
            default -> {
            }
        }
    }

    public static String hint(GraphRelType type) {
        return switch (type) {
            case MEMBER_OF -> "MEMBER_OF: * → CLUSTER";
            case HAS_TAG -> "HAS_TAG: * → TAG";
            case CONNECTS_VIA -> "CONNECTS_VIA: SERVER(目标) → SERVER(跳板)";
            case RUNS_ON -> "RUNS_ON: SERVICE|DATABASE|NETWORK → SERVER|CLUSTER";
            case DEPENDS_ON -> "DEPENDS_ON: 非 TAG/ENVIRONMENT → 非 TAG/ENVIRONMENT";
        };
    }

    public static AssetKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AssetKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    private static void require(
            boolean ok, GraphRelType type, AssetKind from, AssetKind to, String detail) {
        if (!ok) {
            fail(type, from, to, detail);
        }
    }

    private static void fail(GraphRelType type, AssetKind from, AssetKind to, String detail) {
        throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "GRAPH_REL_ENDPOINT_KIND",
                "边端点 kind 不合法: " + type.name() + " from=" + from + " to=" + to
                        + " — " + detail + "（" + hint(type) + "）");
    }
}
