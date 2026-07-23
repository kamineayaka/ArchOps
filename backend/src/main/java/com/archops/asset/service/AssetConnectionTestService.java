package com.archops.asset.service;

import com.archops.asset.domain.SshAuthType;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.common.exception.BusinessException;
import com.archops.terminal.pool.AssetSshDialer;
import java.util.List;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AssetConnectionTestService {

    private static final long TEST_TIMEOUT_MS = 10_000L;

    private final AssetSshDialer assetSshDialer;

    public AssetConnectionTestService(AssetSshDialer assetSshDialer) {
        this.assetSshDialer = assetSshDialer;
    }

    public TestConnectionResponse test(TestConnectionRequest request) {
        long started = System.nanoTime();
        try {
            ClientSession session = openSession(request);
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

    private ClientSession openSession(TestConnectionRequest request) throws Exception {
        if (request.assetId() != null
                && (request.secret() == null || request.secret().isBlank())) {
            return assetSshDialer.dial(request.assetId());
        }

        String host = request.host() != null ? request.host().trim() : "";
        if (host.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NO_HOST", "请填写主机地址");
        }
        String username = request.username() != null ? request.username().trim() : "";
        if (username.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSH_USER_REQUIRED", "请填写 SSH 用户名");
        }
        if (request.authType() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSH_AUTH_REQUIRED", "请选择认证方式");
        }
        if (request.secret() == null || request.secret().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSH_SECRET_REQUIRED", "请填写密码或私钥");
        }
        int port = request.port() != null && request.port() > 0 ? request.port() : 22;
        List<Long> jumps = request.jumpAssetIds() != null ? request.jumpAssetIds() : List.of();
        return assetSshDialer.dialEphemeral(
                jumps,
                host,
                port,
                username,
                request.authType(),
                request.secret(),
                TEST_TIMEOUT_MS);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
