package com.archops.tools.tool;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.service.AssetConnectionTestService;
import com.archops.asset.service.AssetService;
import com.archops.tools.AgentTool;
import com.archops.tools.ToolScope;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Read-only Kubernetes probe (LOW risk): version / namespaces via type handler test path.
 */
@Component
public class K8sGetTool implements AgentTool {

    private final AssetService assetService;
    private final AssetConnectionTestService connectionTestService;

    public K8sGetTool(AssetService assetService, AssetConnectionTestService connectionTestService) {
        this.assetService = assetService;
        this.connectionTestService = connectionTestService;
    }

    @Override
    public String name() {
        return "k8s_get";
    }

    @Override
    public String description() {
        return "Read-only Kubernetes probe for K8S assets (API /version or jump kubectl get ns). "
                + "resource may be version or ns. assetId must be in conversation targets when set.";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{"assetId":{"type":"integer"},"resource":{"type":"string","description":"version or ns"}},"required":["assetId"]}""";
    }

    @Override
    public String execute(Map<String, Object> arguments, ExecutionContext context) {
        Long assetId = asLong(arguments != null ? arguments.get("assetId") : null);
        if (assetId == null) {
            return "Error: assetId is required";
        }
        ToolScope.assertInScope(context.targetAssetIds(), assetId);
        AssetResponse asset = assetService.get(assetId);
        if (asset.kind() != AssetKind.K8S) {
            return "Error: k8s_get only supports K8S assets (got " + asset.kind() + ")";
        }
        String resource = arguments != null && arguments.get("resource") != null
                ? String.valueOf(arguments.get("resource")).trim().toLowerCase(Locale.ROOT)
                : "ns";
        if (!resource.isEmpty() && !resource.equals("ns") && !resource.equals("version") && !resource.equals("namespaces")) {
            return "Error: resource must be version or ns (read-only)";
        }
        TestConnectionResponse probe = connectionTestService.test(new TestConnectionRequest(
                assetId, null, null, null, null, null, null, null, null, null, null));
        return (probe.ok() ? "k8s_get ok" : "k8s_get failed")
                + " asset=" + assetId
                + " resource=" + (resource.isEmpty() ? "ns" : resource)
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
