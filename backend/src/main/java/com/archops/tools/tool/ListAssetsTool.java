package com.archops.tools.tool;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lists managed assets for ops targeting. Not a topology dump — use
 * {@code graph_neighborhood} / {@code graph_path} for edges and paths.
 */
@Component
public class ListAssetsTool implements AgentTool {

    private static final Set<AssetKind> LOGICAL_KINDS = EnumSet.of(AssetKind.TAG, AssetKind.ENVIRONMENT);

    private final AssetService assetService;

    public ListAssetsTool(AssetService assetService) {
        this.assetService = assetService;
    }

    @Override
    public String name() {
        return "list_assets";
    }

    @Override
    public String description() {
        return "List managed assets with id, elementId, name, kind, host, and credential flags. "
                + "Default omits logical TAG/ENVIRONMENT nodes; set includeLogical=true to include them. "
                + "When the conversation has target assets, only those assets are listed. "
                + "This is NOT the inventory graph — use graph_neighborhood / graph_path for topology.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"includeLogical":{"type":"boolean","description":"Include TAG/ENVIRONMENT nodes (default false)"}},"required":[]}
                """;
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        boolean includeLogical = asBoolean(arguments != null ? arguments.get("includeLogical") : null);
        List<AssetResponse> assets = assetService.list(context.userId(), context.roles());
        Set<Long> allowed = ToolScope.allowedSet(context.targetAssetIds());
        if (!allowed.isEmpty()) {
            assets = assets.stream().filter(a -> allowed.contains(a.id())).toList();
        }
        if (!includeLogical) {
            assets = assets.stream().filter(a -> a.kind() == null || !LOGICAL_KINDS.contains(a.kind())).toList();
        }
        if (assets.isEmpty()) {
            return allowed.isEmpty() ? "No assets registered." : "No assets in the current conversation target scope.";
        }
        return assets.stream()
                .map(a -> "- id=" + a.id()
                        + " elementId=" + (a.elementId() != null ? a.elementId() : "n/a")
                        + " name=" + a.name()
                        + " kind=" + a.kind()
                        + " host=" + (a.host() != null ? a.host() : "n/a")
                        + " hasCredential=" + a.hasSshCredential())
                .collect(Collectors.joining("\n"));
    }

    private static boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return false;
    }
}
