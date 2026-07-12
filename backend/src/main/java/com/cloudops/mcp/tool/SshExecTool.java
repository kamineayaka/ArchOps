package com.cloudops.mcp.tool;

import com.cloudops.asset.dto.AssetResponse;
import com.cloudops.asset.service.AssetService;
import com.cloudops.mcp.McpTool;
import com.cloudops.terminal.pool.PooledSshHandle;
import com.cloudops.terminal.pool.SshConnectionPool;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.springframework.stereotype.Component;

/**
 * Executes shell commands via the shared SSH connection pool.
 * Uses conversation target assets when {@code assetId} is omitted.
 */
@Component
public class SshExecTool implements McpTool {

    private final AssetService assetService;
    private final SshConnectionPool sshConnectionPool;

    public SshExecTool(AssetService assetService, SshConnectionPool sshConnectionPool) {
        this.assetService = assetService;
        this.sshConnectionPool = sshConnectionPool;
    }

    @Override
    public String name() {
        return "ssh_exec";
    }

    @Override
    public String description() {
        return "Execute a shell command on a managed Linux asset via SSH and return combined stdout/stderr. "
                + "If assetId is omitted, uses the conversation's active target asset. "
                + "Use for read-only diagnostics like df, free, docker ps, kubectl get. "
                + "Destructive commands require prior approval.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"assetId":{"type":"integer","description":"ID of the target asset (optional if conversation has target assets)"},"command":{"type":"string","description":"Shell command to execute"}},"required":["command"]}""";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) throws Exception {
        Long assetId = resolveAssetId(arguments, context);
        String command = String.valueOf(arguments.get("command"));

        AssetResponse asset = assetService.get(assetId);
        if (asset.host() == null) {
            return "Error: asset has no host configured";
        }

        try (PooledSshHandle pooled = sshConnectionPool.acquire(context.userId(), assetId)) {
            try (ClientChannel channel = pooled.session().createExecChannel(command);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                channel.setOut(out);
                channel.setErr(out);
                channel.open().verify(15_000);
                channel.waitFor(java.util.EnumSet.of(ClientChannelEvent.CLOSED), 30_000);
                return out.toString();
            }
        } catch (Exception ex) {
            sshConnectionPool.remove(context.userId(), assetId);
            throw ex;
        }
    }

    private Long resolveAssetId(Map<String, Object> arguments, ExecutionContext context) {
        Object raw = arguments.get("assetId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        List<Long> targets = context.targetAssetIds();
        if (targets != null && !targets.isEmpty()) {
            return targets.getFirst();
        }
        throw new IllegalArgumentException(
                "No target asset specified. Set conversation target assets or pass assetId.");
    }
}
