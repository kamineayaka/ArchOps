package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.common.exception.BusinessException;
import com.archops.terminal.pool.AssetSshDialer;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Minimal Kubernetes asset: API server + token, or jump host + kubectl.
 * No full control-plane UI (connectAction=PAGE deferred).
 */
@Component
public class K8sAssetTypeHandler extends AbstractAssetTypeHandler {

    public static final String MODE_API = "API_SERVER";
    public static final String MODE_JUMP = "JUMP_KUBECTL";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(8);

    private final AssetSshDialer assetSshDialer;
    private final HttpClient insecureHttpClient;

    public K8sAssetTypeHandler(AssetSshDialer assetSshDialer) {
        this.assetSshDialer = assetSshDialer;
        this.insecureHttpClient = buildInsecureClient();
    }

    @Override
    public String type() {
        return AssetKind.K8S.name();
    }

    @Override
    public int defaultPort() {
        return 6443;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }

    @Override
    public ConnectAction connectAction() {
        return ConnectAction.PAGE;
    }

    @Override
    public void validateCreate(AssetRequest req) {
        validateCommon(req);
        String mode = normalizeMode(req.k8sMode());
        if (MODE_API.equals(mode)) {
            if (!StringUtils.hasText(req.apiServerUrl()) && !StringUtils.hasText(req.host())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "K8S_API_REQUIRED", "请填写 API Server URL 或主机");
            }
        } else if (MODE_JUMP.equals(mode)) {
            if (req.jumpAssetIds() == null || req.jumpAssetIds().isEmpty()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "K8S_JUMP_REQUIRED", "跳板 kubectl 模式请选择跳板资产");
            }
        } else {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "K8S_MODE_INVALID", "K8s 模式须为 API_SERVER 或 JUMP_KUBECTL");
        }
    }

    @Override
    public void validateUpdate(AssetRequest req) {
        validateCreate(req);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectivityContext ctx) {
        long started = System.nanoTime();
        try {
            String mode = normalizeMode(ctx.k8sMode());
            if (MODE_JUMP.equals(mode)) {
                return probeJumpKubectl(ctx, started);
            }
            return probeApiServer(ctx, started);
        } catch (BusinessException e) {
            return new TestConnectionResponse(false, elapsedMs(started), e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return new TestConnectionResponse(false, elapsedMs(started), "连接失败: " + msg);
        }
    }

    private TestConnectionResponse probeApiServer(ConnectivityContext ctx, long started) throws Exception {
        String url = resolveApiUrl(ctx);
        if (!StringUtils.hasText(ctx.secret())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "K8S_TOKEN_REQUIRED", "请填写 API Bearer Token");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/version"))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + ctx.secret().trim())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = insecureHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return new TestConnectionResponse(
                    true, elapsedMs(started), "API /version 探活成功 (HTTP " + response.statusCode() + ")");
        }
        return new TestConnectionResponse(
                false,
                elapsedMs(started),
                "API /version 失败: HTTP " + response.statusCode());
    }

    private TestConnectionResponse probeJumpKubectl(ConnectivityContext ctx, long started) throws Exception {
        List<Long> jumps = ctx.jumpsOrEmpty();
        if (jumps.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "K8S_JUMP_REQUIRED", "请选择跳板资产");
        }
        Long jumpId = jumps.getFirst();
        ClientSession session = assetSshDialer.dial(jumpId);
        try {
            String out = exec(session, "kubectl get ns --request-timeout=5s 2>&1 | head -n 20");
            if (out.toLowerCase().contains("error") && !out.contains("NAME")) {
                return new TestConnectionResponse(false, elapsedMs(started), "kubectl 探活失败: " + trim(out));
            }
            return new TestConnectionResponse(
                    true, elapsedMs(started), "跳板 kubectl get ns 成功: " + trim(out));
        } finally {
            try {
                session.close(false);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private static String exec(ClientSession session, String command) throws Exception {
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                ClientChannel channel = session.createExecChannel(command)) {
            channel.setOut(stdout);
            channel.setErr(stderr);
            channel.open().verify(Duration.ofSeconds(8));
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(15).toMillis());
            String combined = stdout.toString() + stderr;
            return combined.isBlank() ? "(empty)" : combined;
        }
    }

    private static String resolveApiUrl(ConnectivityContext ctx) {
        if (StringUtils.hasText(ctx.apiServerUrl())) {
            String url = ctx.apiServerUrl().trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
        if (!StringUtils.hasText(ctx.host())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "K8S_API_REQUIRED", "请填写 API Server URL");
        }
        int port = ctx.port() != null && ctx.port() > 0 ? ctx.port() : 6443;
        return "https://" + ctx.host().trim() + ":" + port;
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return MODE_API;
        }
        return mode.trim().toUpperCase();
    }

    private static String trim(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static HttpClient buildInsecureClient() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .sslContext(ssl)
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        }
    }
}
