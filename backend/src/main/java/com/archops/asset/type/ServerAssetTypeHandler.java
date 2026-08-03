package com.archops.asset.type;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.common.exception.BusinessException;
import com.archops.terminal.pool.AssetSshDialer;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ServerAssetTypeHandler extends AbstractAssetTypeHandler {

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
    public TestConnectionResponse testConnection(ConnectivityContext ctx) {
        long started = System.nanoTime();
        try {
            if (ctx.assetId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_ID_REQUIRED", "请使用已保存资产测试连接");
            }
            ClientSession session =
                    assetSshDialer.dial(ctx.assetId(), ctx.userId(), ctx.roles());
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

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
