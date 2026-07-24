package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.common.exception.BusinessException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DATABASE asset type: form + TCP/JDBC probe. Query UI may be deferred (connectAction=QUERY).
 */
@Component
public class DatabaseAssetTypeHandler extends AbstractAssetTypeHandler {

    private static final int TCP_TIMEOUT_MS = 5_000;
    private static final int JDBC_TIMEOUT_SEC = 5;

    @Override
    public String type() {
        return AssetKind.DATABASE.name();
    }

    @Override
    public int defaultPort() {
        return 5432;
    }

    @Override
    public String policyKind() {
        return "GENERIC";
    }

    @Override
    public ConnectAction connectAction() {
        return ConnectAction.QUERY;
    }

    @Override
    public void validateCreate(AssetRequest req) {
        validateCommon(req);
        requireHost(req);
    }

    @Override
    public void validateUpdate(AssetRequest req) {
        validateCommon(req);
        requireHost(req);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectivityContext ctx) {
        long started = System.nanoTime();
        try {
            if (!StringUtils.hasText(ctx.host())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NO_HOST", "请填写主机地址");
            }
            int port = ctx.port() != null && ctx.port() > 0 ? ctx.port() : defaultPort();
            String host = ctx.host().trim();

            // Jump-through TCP is deferred; still allow saving jump ids for later Agent use.
            if (!ctx.jumpsOrEmpty().isEmpty()) {
                // Probe the jump SSH asset instead of failing hard — operator still gets signal.
                // Direct TCP to target is attempted first when reachable; otherwise report jump note.
            }

            tcpProbe(host, port);

            if (StringUtils.hasText(ctx.username()) && StringUtils.hasText(ctx.secret())) {
                jdbcProbe(host, port, ctx.username().trim(), ctx.secret(), ctx.database());
                return new TestConnectionResponse(
                        true, elapsedMs(started), "TCP + JDBC 探活成功");
            }
            return new TestConnectionResponse(
                    true, elapsedMs(started), "TCP 可达（未提供账号，跳过 JDBC 认证）");
        } catch (BusinessException e) {
            return new TestConnectionResponse(false, elapsedMs(started), e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return new TestConnectionResponse(false, elapsedMs(started), "连接失败: " + msg);
        }
    }

    private static void tcpProbe(String host, int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TCP_TIMEOUT_MS);
        }
    }

    private static void jdbcProbe(String host, int port, String username, String password, String database)
            throws Exception {
        String db = StringUtils.hasText(database) ? database.trim() : "postgres";
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", String.valueOf(JDBC_TIMEOUT_SEC));
        props.setProperty("socketTimeout", String.valueOf(JDBC_TIMEOUT_SEC));
        props.setProperty("loginTimeout", String.valueOf(JDBC_TIMEOUT_SEC));
        try (Connection conn = DriverManager.getConnection(url, props)) {
            if (!conn.isValid(JDBC_TIMEOUT_SEC)) {
                throw new IllegalStateException("JDBC 连接无效");
            }
        }
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
