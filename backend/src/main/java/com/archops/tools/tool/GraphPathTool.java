package com.archops.tools.tool;

import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.hybrid.GraphContextRetriever;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Read-only shortest topology path + CONNECTS_VIA jump chain. */
@Component
public class GraphPathTool implements AgentTool {

    private final GraphContextRetriever graphContextRetriever;
    private final AssetAclService assetAclService;

    public GraphPathTool(
            GraphContextRetriever graphContextRetriever,
            AssetAclService assetAclService) {
        this.graphContextRetriever = graphContextRetriever;
        this.assetAclService = assetAclService;
    }

    @Override
    public String name() {
        return "graph_path";
    }

    @Override
    public String description() {
        return "Read-only: find a shortest Neo4j topology path between two assets and show "
                + "CONNECTS_VIA SSH jump chain for the destination. Use for dependency / routing questions.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"fromAssetId":{"type":"integer","description":"Start asset id"},"toAssetId":{"type":"integer","description":"End asset id"}},"required":["fromAssetId","toAssetId"]}
                """;
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        Long from = asLong(arguments != null ? arguments.get("fromAssetId") : null);
        Long to = asLong(arguments != null ? arguments.get("toAssetId") : null);
        if (from == null || to == null) {
            return "fromAssetId and toAssetId are required.";
        }
        ToolScope.assertInScope(context.targetAssetIds(), from);
        ToolScope.assertInScope(context.targetAssetIds(), to);
        assetAclService.requireAssetAccess(context.userId(), context.roles(), from);
        assetAclService.requireAssetAccess(context.userId(), context.roles(), to);
        return graphContextRetriever.describePath(
                from,
                to,
                assetAclService.allowedAssetIds(context.userId(), context.roles()));
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
