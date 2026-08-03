package com.archops.tools.tool;

import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.hybrid.GraphContextRetriever;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Read-only graph neighborhood for hybrid RAG deep dive. */
@Component
public class GraphNeighborhoodTool implements AgentTool {

    private final GraphContextRetriever graphContextRetriever;
    private final AssetAclService assetAclService;

    public GraphNeighborhoodTool(
            GraphContextRetriever graphContextRetriever,
            AssetAclService assetAclService) {
        this.graphContextRetriever = graphContextRetriever;
        this.assetAclService = assetAclService;
    }

    @Override
    public String name() {
        return "graph_neighborhood";
    }

    @Override
    public String description() {
        return "Read-only: inspect Neo4j topology neighborhood (MEMBER_OF, RUNS_ON, DEPENDS_ON, "
                + "CONNECTS_VIA, HAS_TAG) around one or more assets. Prefer this when Graph neighborhood "
                + "in context is insufficient. Respects conversation target asset scope.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"assetId":{"type":"integer","description":"Center asset id (pgAssetId)"},"assetIds":{"type":"array","items":{"type":"integer"},"description":"Optional multiple seed asset ids"},"hops":{"type":"integer","description":"1 or 2 (default 2)"},"maxNodes":{"type":"integer","description":"Cap nodes/edges in the answer (default 30)"}},"required":[]}
                """;
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        List<Long> seeds = new ArrayList<>();
        Object multi = arguments != null ? arguments.get("assetIds") : null;
        if (multi instanceof List<?> list) {
            for (Object item : list) {
                Long id = asLong(item);
                if (id != null) {
                    ToolScope.assertInScope(context.targetAssetIds(), id);
                    assetAclService.requireAssetAccess(context.userId(), context.roles(), id);
                    seeds.add(id);
                }
            }
        }
        Long single = asLong(arguments != null ? arguments.get("assetId") : null);
        if (single != null) {
            ToolScope.assertInScope(context.targetAssetIds(), single);
            assetAclService.requireAssetAccess(context.userId(), context.roles(), single);
            seeds.add(single);
        }
        if (seeds.isEmpty() && context.targetAssetIds() != null) {
            seeds.addAll(context.targetAssetIds());
        }
        if (seeds.isEmpty()) {
            return "No assetId provided and conversation has no target assets.";
        }

        int hops = asInt(arguments != null ? arguments.get("hops") : null, GraphContextRetriever.DEFAULT_HOPS);
        int maxNodes = asInt(arguments != null ? arguments.get("maxNodes") : null, GraphContextRetriever.DEFAULT_MAX_NODES);
        var result = graphContextRetriever.neighborhood(
                seeds,
                hops,
                maxNodes,
                assetAclService.allowedAssetIds(context.userId(), context.roles()));
        return result.promptText();
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

    private static int asInt(Object raw, int fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
