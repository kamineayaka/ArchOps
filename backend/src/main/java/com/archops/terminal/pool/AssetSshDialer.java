package com.archops.terminal.pool;

import com.archops.asset.domain.SshAuthType;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.common.config.SshPoolProperties;
import com.archops.common.exception.BusinessException;
import com.archops.graph.service.GraphConnectPathService;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves asset credentials and opens authenticated SSH sessions.
 * Jump hops use Neo4j {@code CONNECTS_VIA} (graph SSOT) via {@link GraphConnectPathService}.
 */
@Component
public class AssetSshDialer {

    private final AssetService assetService;
    private final SshClient sshClient;
    private final SshPoolProperties properties;
    private final GraphConnectPathService graphConnectPathService;

    public AssetSshDialer(
            @Lazy AssetService assetService,
            SshClient sshClient,
            SshPoolProperties properties,
            GraphConnectPathService graphConnectPathService) {
        this.assetService = assetService;
        this.sshClient = sshClient;
        this.properties = properties;
        this.graphConnectPathService = graphConnectPathService;
    }

    public ClientSession dial(Long assetId) throws Exception {
        List<Long> jumps = graphConnectPathService.resolveJumpAssetIds(assetId);
        if (jumps == null || jumps.isEmpty()) {
            return dialAssetDirect(assetId);
        }
        validateJumpChain(jumps, assetId);
        return dialViaJumpChain(jumps, assetId);
    }

    /**
     * Detects cycles in {@code jumpAssetIds + targetAssetId}.
     * Package-visible for unit tests.
     */
    static void validateJumpChain(List<Long> jumpAssetIds, Long targetAssetId) {
        if (jumpAssetIds == null || jumpAssetIds.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        List<Long> path = new ArrayList<>(jumpAssetIds);
        path.add(targetAssetId);
        for (Long id : path) {
            if (id == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "SSH_JUMP_INVALID", "跳板链包含无效资产 ID");
            }
            if (!seen.add(id)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "SSH_JUMP_CYCLE",
                        "跳板链存在循环，资产 ID: " + id);
            }
        }
    }

    /**
     * Multi-hop dial: connect first jump directly, then for each next hop open a
     * local port forward and authenticate with that hop's credentials.
     */
    ClientSession dialViaJumpChain(List<Long> jumpAssetIds, Long targetAssetId) throws Exception {
        List<Long> path = new ArrayList<>(jumpAssetIds);
        path.add(targetAssetId);

        List<ClientSession> hopSessions = new ArrayList<>();
        List<ExplicitPortForwardingTracker> trackers = new ArrayList<>();
        try {
            ClientSession current = dialAssetDirect(path.get(0));
            hopSessions.add(current);

            for (int i = 1; i < path.size(); i++) {
                Long nextId = path.get(i);
                AssetResponse next = requireHost(nextId);
                int nextPort = next.port() != null ? next.port() : 22;
                SshdSocketAddress remote = new SshdSocketAddress(next.host(), nextPort);
                ExplicitPortForwardingTracker tracker = current.createLocalPortForwardingTracker(
                        SshdSocketAddress.LOCALHOST_ADDRESS, remote);
                trackers.add(tracker);
                SshdSocketAddress bound = tracker.getBoundAddress();
                current = connectAndAuth(nextId, bound.getHostName(), bound.getPort());
                hopSessions.add(current);
            }

            ClientSession target = hopSessions.get(hopSessions.size() - 1);
            List<ClientSession> jumps = List.copyOf(hopSessions.subList(0, hopSessions.size() - 1));
            List<ExplicitPortForwardingTracker> heldTrackers = List.copyOf(trackers);
            target.addCloseFutureListener(f -> {
                for (int i = heldTrackers.size() - 1; i >= 0; i--) {
                    try {
                        heldTrackers.get(i).close();
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                }
                for (int i = jumps.size() - 1; i >= 0; i--) {
                    try {
                        jumps.get(i).close(false);
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                }
            });
            return target;
        } catch (BusinessException e) {
            closeQuietly(hopSessions, trackers);
            throw e;
        } catch (Exception e) {
            closeQuietly(hopSessions, trackers);
            throw e;
        }
    }

    /** Direct dial without reading jump chain again (used for first hop and empty chain). */
    ClientSession dialAssetDirect(Long assetId) throws Exception {
        AssetResponse asset = requireHost(assetId);
        if (asset.kind() != com.archops.asset.domain.AssetKind.SERVER) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "SSH_ASSET_KIND",
                    "仅 SERVER 资产支持 SSH 连接: assetId=" + assetId + " kind=" + asset.kind());
        }
        SshCredential credential;
        try {
            credential = assetService.getSshCredential(assetId);
        } catch (BusinessException e) {
            if ("CREDENTIAL_NOT_FOUND".equals(e.getCode())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "SSH_JUMP_MISSING_CREDENTIAL",
                        "跳板或目标资产未配置 SSH 凭证: assetId=" + assetId);
            }
            throw e;
        }
        int port = asset.port() != null ? asset.port() : 22;
        return connectAndAuth(assetId, asset.host(), port, credential);
    }

    private ClientSession connectAndAuth(Long assetId, String host, int port) throws Exception {
        SshCredential credential;
        try {
            credential = assetService.getSshCredential(assetId);
        } catch (BusinessException e) {
            if ("CREDENTIAL_NOT_FOUND".equals(e.getCode())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "SSH_JUMP_MISSING_CREDENTIAL",
                        "跳板或目标资产未配置 SSH 凭证: assetId=" + assetId);
            }
            throw e;
        }
        return connectAndAuth(assetId, host, port, credential);
    }

    private ClientSession connectAndAuth(Long assetId, String host, int port, SshCredential credential)
            throws Exception {
        String secret = assetService.decryptSecret(credential);
        long timeoutMs = properties.connectTimeout().toMillis();
        return connectAndAuthRaw(
                host, port, credential.getUsername(), credential.getAuthType(), secret, timeoutMs);
    }

    private ClientSession connectAndAuthRaw(
            String host,
            int port,
            String username,
            SshAuthType authType,
            String secret,
            long timeoutMs) throws Exception {
        ClientSession session = sshClient.connect(username, host, port)
                .verify(timeoutMs)
                .getSession();
        try {
            if (authType == SshAuthType.PASSWORD) {
                session.addPasswordIdentity(secret);
            } else {
                KeyPair keyPair = SshKeyLoader.loadPrivateKey(secret);
                session.addPublicKeyIdentity(keyPair);
            }
            session.auth().verify(timeoutMs);
            return session;
        } catch (Exception e) {
            try {
                session.close(false);
            } catch (Exception ignored) {
                // best-effort
            }
            throw e;
        }
    }

    private AssetResponse requireHost(Long assetId) {
        AssetResponse asset = assetService.get(assetId);
        if (asset.host() == null || asset.host().isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ASSET_NO_HOST",
                    "资产未配置主机地址: assetId=" + assetId);
        }
        return asset;
    }

    private static void closeQuietly(
            List<ClientSession> sessions, List<ExplicitPortForwardingTracker> trackers) {
        for (int i = trackers.size() - 1; i >= 0; i--) {
            try {
                trackers.get(i).close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        for (int i = sessions.size() - 1; i >= 0; i--) {
            try {
                sessions.get(i).close(false);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}
