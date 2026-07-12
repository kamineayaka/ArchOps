package com.cloudops.terminal.pool;

import com.cloudops.asset.domain.SshAuthType;
import com.cloudops.asset.domain.SshCredential;
import com.cloudops.asset.dto.AssetResponse;
import com.cloudops.asset.service.AssetService;
import com.cloudops.common.config.SshPoolProperties;
import com.cloudops.common.exception.BusinessException;
import java.security.KeyPair;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves asset credentials and opens authenticated SSH sessions.
 * Shared by the connection pool, AI tools, and web terminal.
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
        AssetResponse asset = assetService.get(assetId);
        if (asset.host() == null || asset.host().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NO_HOST", "资产未配置主机地址");
        }

        SshCredential credential = assetService.getSshCredential(assetId);
        String secret = assetService.decryptSecret(credential);
        int port = asset.port() != null ? asset.port() : 22;
        long timeoutMs = properties.connectTimeout().toMillis();

        ClientSession session = sshClient.connect(credential.getUsername(), asset.host(), port)
                .verify(timeoutMs)
                .getSession();

        if (credential.getAuthType() == SshAuthType.PASSWORD) {
            session.addPasswordIdentity(secret);
        } else {
            KeyPair keyPair = SshKeyLoader.loadPrivateKey(secret);
            session.addPublicKeyIdentity(keyPair);
        }
        session.auth().verify(timeoutMs);
        return session;
    }
}
