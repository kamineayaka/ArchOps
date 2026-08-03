package com.archops.tools.tool;

import com.archops.asset.dbquery.DbQueryResponse;
import com.archops.asset.dbquery.DbQueryService;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Runs SQL against a DATABASE asset. Read SQL auto-executes; mutating SQL is gated by
 * {@link com.archops.ai.service.ToolExecutorService} / RiskClassifier (HIGH → always approval).
 */
@Component
public class DbQueryTool implements AgentTool {

    private final DbQueryService dbQueryService;
    private final AssetService assetService;

    public DbQueryTool(DbQueryService dbQueryService, AssetService assetService) {
        this.dbQueryService = dbQueryService;
        this.assetService = assetService;
    }

    @Override
    public String name() {
        return "db_query";
    }

    @Override
    public String description() {
        return "Execute SQL against a managed DATABASE asset (PostgreSQL first). "
                + "Requires assetId of a DATABASE asset within conversation targets when targets are set. "
                + "Read-only statements (SELECT/SHOW/EXPLAIN/WITH…SELECT) run immediately. "
                + "Mutating SQL (INSERT/UPDATE/DELETE/DDL/…) requires prior approval. "
                + "Results are capped (default 500 rows) with a statement timeout.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"assetId":{"type":"integer","description":"ID of the DATABASE asset"},"sql":{"type":"string","description":"Single SQL statement to execute"}},"required":["assetId","sql"]}""";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) throws Exception {
        Object rawId = arguments.get("assetId");
        if (!(rawId instanceof Number number)) {
            throw new IllegalArgumentException("assetId is required");
        }
        Long assetId = number.longValue();
        ToolScope.assertInScope(context.targetAssetIds(), assetId);

        Object rawSql = arguments.get("sql");
        if (rawSql == null || String.valueOf(rawSql).isBlank()) {
            throw new IllegalArgumentException("sql is required");
        }
        String sql = String.valueOf(rawSql);

        AssetResponse asset = assetService.get(assetId, context.userId(), context.roles());
        if (!"DATABASE".equalsIgnoreCase(String.valueOf(asset.kind()))) {
            return "Error: asset " + assetId + " is not a DATABASE (kind=" + asset.kind() + ")";
        }

        DbQueryResponse result =
                dbQueryService.runForTool(context.userId(), context.roles(), assetId, sql);
        return format(asset, result);
    }

    private static String format(AssetResponse asset, DbQueryResponse result) {
        StringBuilder sb = new StringBuilder();
        sb.append("asset=")
                .append(asset.name())
                .append(" (id=")
                .append(asset.id())
                .append(", host=")
                .append(asset.host() != null ? asset.host() : "n/a")
                .append(")\n");
        sb.append("status=")
                .append(result.status())
                .append(" mutating=")
                .append(result.mutating())
                .append(" risk=")
                .append(result.riskLevel())
                .append('\n');
        if (result.message() != null) {
            sb.append(result.message()).append('\n');
        }
        if (result.mutating() && result.updateCount() > 0) {
            sb.append("updateCount=").append(result.updateCount()).append('\n');
        }
        if (result.columns() != null && !result.columns().isEmpty()) {
            sb.append(String.join(" | ", result.columns())).append('\n');
            List<List<Object>> rows = result.rows() != null ? result.rows() : List.of();
            int limit = Math.min(rows.size(), 50);
            for (int i = 0; i < limit; i++) {
                sb.append(rows.get(i).stream()
                                .map(v -> v == null ? "NULL" : String.valueOf(v))
                                .collect(Collectors.joining(" | ")))
                        .append('\n');
            }
            if (rows.size() > limit) {
                sb.append("… (").append(rows.size() - limit).append(" more rows omitted in tool output)\n");
            }
            sb.append("rowCount=").append(result.rowCount());
            if (result.truncated()) {
                sb.append(" (truncated)");
            }
        }
        if (result.elapsedMs() != null) {
            sb.append("\nelapsedMs=").append(result.elapsedMs());
        }
        return sb.toString().trim();
    }
}
