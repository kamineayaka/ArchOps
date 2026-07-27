package com.archops.graph.domain;

import com.archops.asset.domain.AssetKind;
import com.archops.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Maps AssetKind ↔ Neo4j labels (:Asset + specialization). */
public final class GraphLabels {

    private static final Set<String> ALLOWED = Set.of(
            "Asset", "Server", "Cluster", "Service", "Database", "Network", "Tag", "Environment", "Deleted");

    private GraphLabels() {}

    public static String specialization(AssetKind kind) {
        return switch (kind) {
            case SERVER -> "Server";
            case CLUSTER -> "Cluster";
            case SERVICE -> "Service";
            case DATABASE -> "Database";
            case NETWORK -> "Network";
            case TAG -> "Tag";
            case ENVIRONMENT -> "Environment";
        };
    }

    public static List<String> forKind(AssetKind kind) {
        return List.of("Asset", specialization(kind));
    }

    /** Validate and normalize labels from ChangeSet; always ensure Asset is first. */
    public static List<String> normalize(List<String> raw, AssetKind kind) {
        List<String> labels = new ArrayList<>();
        labels.add("Asset");
        String expected = specialization(kind);
        if (raw != null) {
            for (String label : raw) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                String matched = matchAllowed(label);
                if (matched == null) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "GRAPH_LABEL_INVALID", "非法 Neo4j label: " + label);
                }
                if ("Asset".equals(matched) || labels.contains(matched)) {
                    continue;
                }
                labels.add(matched);
            }
        }
        if (!labels.contains(expected)) {
            labels.add(expected);
        }
        // Reject conflicting specializations
        EnumSet<AssetKind> kinds = EnumSet.noneOf(AssetKind.class);
        for (AssetKind k : AssetKind.values()) {
            if (labels.contains(specialization(k))) {
                kinds.add(k);
            }
        }
        if (kinds.size() > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "GRAPH_LABEL_CONFLICT", "节点不能同时带多种资产特化 label");
        }
        return List.copyOf(labels);
    }

    public static String cypherLabelSuffix(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (String label : labels) {
            if (!ALLOWED.contains(label)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "GRAPH_LABEL_INVALID", "非法 Neo4j label: " + label);
            }
            sb.append(':').append(label);
        }
        return sb.toString();
    }

    private static String matchAllowed(String raw) {
        for (String allowed : ALLOWED) {
            if (allowed.equalsIgnoreCase(raw)) {
                return allowed;
            }
        }
        // kind-style SERVER -> Server
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (AssetKind kind : AssetKind.values()) {
            if (kind.name().equals(upper)) {
                return specialization(kind);
            }
        }
        return null;
    }
}
