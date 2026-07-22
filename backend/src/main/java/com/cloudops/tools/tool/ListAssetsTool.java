package com.cloudops.tools.tool;

import com.cloudops.asset.dto.AssetResponse;
import com.cloudops.asset.service.AssetService;
import com.cloudops.tools.AgentTool;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Lists all registered assets so the agent can pick a target for other tools. */
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
        return "List all managed assets (servers, clusters, services) with their IDs, names, hosts and kinds.";
    }

    @Override
    public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        List<AssetResponse> assets = assetService.list();
        if (assets.isEmpty()) {
            return "No assets registered.";
        }
        return assets.stream()
                .map(a -> "- id=" + a.id() + " name=" + a.name() + " kind=" + a.kind()
                        + " host=" + (a.host() != null ? a.host() : "n/a"))
                .collect(Collectors.joining("\n"));
    }
}
