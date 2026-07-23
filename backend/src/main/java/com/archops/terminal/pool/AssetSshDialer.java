package com.archops.terminal.pool;

import com.archops.asset.domain.SshAuthType;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.common.config.SshPoolProperties;
import com.archops.common.exception.BusinessException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves asset credentials and opens authenticated SSH sessions.
 * Shared by the connection pool, AI tools, and web terminal.
 *
 * <p>Jump / proxy chains are connection topology stored on {@link SshCredential},
 * not Architecture SSOT. Empty jump list uses a direct dial.
 */
@Component
public class AssetSshDialer {

    private final AssetService assetService;
    private final SshClient sshClient;
    private final SshPoolProperties properties;

    public AssetSshDialer(AssetService assetService, SshClient sshClient, SshPoolProperties properties) {
        this.assetService = assetService;
        this.sshClient = sshClient;
        this.properties = properties;
    }

    public ClientSession dial(Long assetId) throws Exception {
        SshCredential credential = assetService.getSshCredential(assetId);
        List<Long> jumps = credential.getJumpAssetIds();
        if (jumps == null || jumps.isEmpty()) {
            return dialAssetDirect(assetId);
        }
        validateJumpChain(jumps, assetId);
        return dialViaJumpChain(jumps, assetId);
    }

    /**
     * Ephemeral dial for "test connection" from the create/edit form.
     * Jump hops use saved credentials; the final target uses the provided secret.
     * Same SSH client / dialer path as the pool and WebSSH (no separate code path).
     */
    public ClientSession dialEphemeral(
            List<Long> jumpAssetIds,
            String host,
            int port,
            String username,
            SshAuthType authType,
            String secret,
            long timeoutMs) throws Exception {
        List<Long> jumps = jumpAssetIds != null ? jumpAssetIds : List.of();
        if (jumps.isEmpty()) {
            return connectAndAuthRaw(host, port, username, authType, secret, timeoutMs);
        }
        validateJumpList(jumps);
        List<ClientSession> hopSessions = new ArrayList<>();
        List<ExplicitPortForwardingTracker> trackers = new ArrayList<>();
        try {
            ClientSession current = dialAssetDirect(jumps.get(0));
            hopSessions.add(current);
            for (int i = 1; i < jumps.size(); i++) {
                Long nextId = jumps.get(i);
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
            SshdSocketAddress remoteTarget = new SshdSocketAddress(host, port);
            ExplicitPortForwardingTracker targetTracker = current.createLocalPortForwardingTracker(
                    SshdSocketAddress.LOCALHOST_ADDRESS, remoteTarget);
            trackers.add(targetTracker);
            SshdSocketAddress boundTarget = targetTracker.getBoundAddress();
            ClientSession target = connectAndAuthRaw(
                    boundTarget.getHostName(),
                    boundTarget.getPort(),
                    username,
                    authType,
                    secret,
                    timeoutMs);
            List<ClientSession> heldJumps = List.copyOf(hopSessions);
            List<ExplicitPortForwardingTracker> heldTrackers = List.copyOf(trackers);
            target.addCloseFutureListener(f -> closeQuietly(heldJumps, heldTrackers));
            return target;
        } catch (BusinessException e) {
            closeQuietly(hopSessions, trackers);
            throw e;
        } catch (Exception e) {
            closeQuietly(hopSessions, trackers);
            throw e;
        }
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

    static void validateJumpList(List<Long> jumpAssetIds) {
        if (jumpAssetIds == null || jumpAssetIds.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : jumpAssetIds) {
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
     * local port forward (DirectTcpip under the hood) and authenticate with that
     * hop's own credentials — same pattern as Apache SSHD ProxyJump.
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
        if (authType == SshAuthType.PASSWORD) {
            session.addPasswordIdentity(secret);
        } else {
            KeyPair keyPair = SshKeyLoader.loadPrivateKey(secret);
            session.addPublicKeyIdentity(keyPair);
        }
        session.auth().verify(timeoutMs);
        return session;
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
