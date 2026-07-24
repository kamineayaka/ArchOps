package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.SshAuthType;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.common.exception.BusinessException;
import com.archops.terminal.pool.AssetSshDialer;
import java.util.List;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ServerAssetTypeHandler extends AbstractAssetTypeHandler {

    private static final long TEST_TIMEOUT_MS = 10_000L;

    private final AssetSshDialer assetSshDialer;

    public ServerAssetTypeHandler(AssetSshDialer assetSshDialer) {
        this.assetSshDialer = assetSshDialer;
    }

    @Override
    public String type() {
        return AssetKind.SERVER.name();
    }

    @Override
    public int defaultPort() {
        return 22;
    }

    @Override
    public String policyKind() {
        return "SSH";
    }

    @Override
    public ConnectAction connectAction() {
        return ConnectAction.TERMINAL;
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
            ClientSession session = openSession(ctx);
            try {
                if (!session.isAuthenticated()) {
                    return new TestConnectionResponse(false, elapsedMs(started), "SSH 认证未完成");
                }
                return new TestConnectionResponse(true, elapsedMs(started), "连接成功");
            } finally {
                try {
                    session.close(false);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        } catch (BusinessException e) {
            return new TestConnectionResponse(false, elapsedMs(started), e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return new TestConnectionResponse(false, elapsedMs(started), "连接失败: " + msg);
        }
    }

    private ClientSession openSession(ConnectivityContext ctx) throws Exception {
        if (ctx.assetId() != null && !StringUtils.hasText(ctx.secret())) {
            return assetSshDialer.dial(ctx.assetId());
        }
        if (!StringUtils.hasText(ctx.host())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NO_HOST", "请填写主机地址");
        }
        if (!StringUtils.hasText(ctx.username())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSH_USER_REQUIRED", "请填写 SSH 用户名");
        }
        if (ctx.authType() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSH_AUTH_REQUIRED", "请选择认证方式");
        }
        if (!StringUtils.hasText(ctx.secret())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSH_SECRET_REQUIRED", "请填写密码或私钥");
        }
        int port = ctx.port() != null && ctx.port() > 0 ? ctx.port() : defaultPort();
        List<Long> jumps = ctx.jumpsOrEmpty();
        return assetSshDialer.dialEphemeral(
                jumps,
                ctx.host().trim(),
                port,
                ctx.username().trim(),
                ctx.authType() != null ? ctx.authType() : SshAuthType.PASSWORD,
                ctx.secret(),
                TEST_TIMEOUT_MS);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
