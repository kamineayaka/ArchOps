package com.archops.tools.tool;

import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Lists managed assets; optional kind filter; respects conversation target scope. */
@Component
public class ListAssetsTool implements AgentTool {

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
        return "List managed assets with IDs, names, hosts and kinds. "
                + "Optional kind filter (SERVER, DATABASE, REDIS, K8S, …). "
                + "When the conversation has target assets/groups, only those assets are listed.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"kind":{"type":"string","description":"Optional asset kind filter, e.g. DATABASE or REDIS"}},"required":[]}""";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        List<AssetResponse> assets = assetService.list();
        Set<Long> allowed = ToolScope.allowedSet(context.targetAssetIds());
        if (!allowed.isEmpty()) {
            assets = assets.stream().filter(a -> allowed.contains(a.id())).toList();
        }
        String kindFilter = argumentString(arguments, "kind");
        if (kindFilter != null && !kindFilter.isBlank()) {
            String want = kindFilter.trim().toUpperCase(Locale.ROOT);
            assets = assets.stream()
                    .filter(a -> a.kind() != null && want.equals(a.kind().name()))
                    .toList();
        }
        if (assets.isEmpty()) {
            return allowed.isEmpty()
                    ? "No assets registered" + (kindFilter != null ? " for kind=" + kindFilter : "") + "."
                    : "No assets in the current conversation target scope"
                            + (kindFilter != null ? " for kind=" + kindFilter : "") + ".";
        }
        return assets.stream()
                .map(a -> "- id=" + a.id() + " name=" + a.name() + " kind=" + a.kind()
                        + " host=" + (a.host() != null ? a.host() : "n/a"))
                .collect(Collectors.joining("\n"));
    }

    private static String argumentString(Map<String, Object> arguments, String key) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return null;
        }
        return String.valueOf(arguments.get(key));
    }
}
