package com.cloudops.tools.tool;

import com.cloudops.asset.dto.AssetResponse;
import com.cloudops.asset.service.AssetService;
import com.cloudops.terminal.pool.PooledSshHandle;
import com.cloudops.terminal.pool.SshConnectionPool;
import com.cloudops.tools.AgentTool;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.springframework.stereotype.Component;

/**
 * Executes shell commands via the shared SSH connection pool.
 * When {@code assetId} is omitted, runs on all conversation target assets sequentially.
 */
@Component
public class SshExecTool implements AgentTool {

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
        return "Execute a shell command on managed Linux assets via SSH and return combined stdout/stderr. "
                + "If assetId is omitted, runs the command on each conversation target asset sequentially. "
                + "Use for read-only diagnostics like df, free, docker ps, kubectl get. "
                + "Destructive commands require prior approval.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"assetId":{"type":"integer","description":"ID of a single target asset (optional; omit to run on all conversation targets)"},"command":{"type":"string","description":"Shell command to execute"}},"required":["command"]}""";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) throws Exception {
        String command = String.valueOf(arguments.get("command"));
        List<Long> assetIds = resolveAssetIds(arguments, context);

        if (assetIds.size() == 1) {
            return executeOnAsset(assetIds.getFirst(), command, context);
        }

        StringBuilder combined = new StringBuilder();
        for (Long assetId : assetIds) {
            AssetResponse asset = assetService.get(assetId);
            combined.append("=== ")
                    .append(asset.name())
                    .append(" (id=")
                    .append(assetId)
                    .append(", host=")
                    .append(asset.host() != null ? asset.host() : "n/a")
                    .append(") ===\n");
            try {
                combined.append(executeOnAsset(assetId, command, context));
            } catch (Exception ex) {
                combined.append("Error: ").append(ex.getMessage());
            }
            combined.append("\n\n");
        }
        return combined.toString().trim();
    }

    private String executeOnAsset(Long assetId, String command, ExecutionContext context) throws Exception {
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

    private List<Long> resolveAssetIds(Map<String, Object> arguments, ExecutionContext context) {
        Object raw = arguments.get("assetId");
        if (raw instanceof Number number) {
            return List.of(number.longValue());
        }
        List<Long> targets = context.targetAssetIds();
        if (targets != null && !targets.isEmpty()) {
            return new ArrayList<>(targets);
        }
        throw new IllegalArgumentException(
                "No target asset specified. Set conversation target assets or pass assetId.");
    }
}
