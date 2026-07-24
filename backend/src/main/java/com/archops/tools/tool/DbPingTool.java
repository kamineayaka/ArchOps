package com.archops.tools.tool;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetQueryResponse;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.service.AssetConnectionTestService;
import com.archops.asset.service.AssetQueryService;
import com.archops.asset.service.AssetService;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Read-only DATABASE / REDIS probe (LOW risk). Out-of-scope assets are rejected.
 */
@Component
public class DbPingTool implements AgentTool {

    private final AssetService assetService;
    private final AssetConnectionTestService connectionTestService;
    private final AssetQueryService assetQueryService;

    public DbPingTool(
            AssetService assetService,
            AssetConnectionTestService connectionTestService,
            AssetQueryService assetQueryService) {
        this.assetService = assetService;
        this.connectionTestService = connectionTestService;
        this.assetQueryService = assetQueryService;
    }

    @Override
    public String name() {
        return "db_ping";
    }

    @Override
    public String description() {
        return "Read-only connectivity probe for DATABASE or REDIS assets (TCP/JDBC or Redis PING). "
                + "assetId must be in conversation targets when targets are set. Never prints secrets.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"assetId":{"type":"integer","description":"DATABASE or REDIS asset id"}},"required":["assetId"]}""";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        Long assetId = asLong(arguments != null ? arguments.get("assetId") : null);
        if (assetId == null) {
            return "Error: assetId is required";
        }
        ToolScope.assertInScope(context.targetAssetIds(), assetId);
        AssetResponse asset = assetService.get(assetId);
        if (asset.kind() != AssetKind.DATABASE && asset.kind() != AssetKind.REDIS) {
            return "Error: db_ping only supports DATABASE or REDIS (got " + asset.kind() + ")";
        }
        TestConnectionResponse probe = connectionTestService.test(new TestConnectionRequest(
                assetId, null, null, null, null, null, null, null, null, null, null));
        if (probe.ok() && asset.kind() == AssetKind.REDIS) {
            AssetQueryResponse ping = assetQueryService.query(assetId, "PING");
            return "db_ping ok asset=" + assetId + " kind=REDIS latencyMs=" + probe.latencyMs()
                    + " detail=" + (ping.ok() ? ping.message() : probe.message());
        }
        return (probe.ok() ? "db_ping ok" : "db_ping failed")
                + " asset=" + assetId
                + " kind=" + asset.kind()
                + " latencyMs=" + probe.latencyMs()
                + " detail=" + probe.message();
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
