package com.archops.asset.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.SshAuthType;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.asset.type.AssetTypeHandler;
import com.archops.asset.type.AssetTypeRegistry;
import com.archops.asset.type.ConnectivityContext;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Dispatches connectivity probes to the registered {@link AssetTypeHandler} for the asset kind.
 * No shared {@code switch(kind)}.
 */
@Service
public class AssetConnectionTestService {

    private final AssetTypeRegistry assetTypeRegistry;
    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;

    public AssetConnectionTestService(
            AssetTypeRegistry assetTypeRegistry,
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            ObjectMapper objectMapper) {
        this.assetTypeRegistry = assetTypeRegistry;
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
    }

    public TestConnectionResponse test(TestConnectionRequest request) {
        try {
            ConnectivityContext ctx = buildContext(request);
            AssetKind kind = resolveKind(request);
            AssetTypeHandler handler = assetTypeRegistry.findRequired(kind.name());
            return handler.testConnection(ctx);
        } catch (BusinessException e) {
            return new TestConnectionResponse(false, 0L, e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return new TestConnectionResponse(false, 0L, "连接失败: " + msg);
        }
    }

    private AssetKind resolveKind(TestConnectionRequest request) {
        if (request.kind() != null) {
            return request.kind();
        }
        if (request.assetId() != null) {
            return assetRepository.findById(request.assetId())
                    .map(Asset::getKind)
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
        }
        // Legacy ephemeral SSH forms may omit kind — default SERVER.
        return AssetKind.SERVER;
    }

    private ConnectivityContext buildContext(TestConnectionRequest request) {
        if (request.assetId() != null && !StringUtils.hasText(request.secret())) {
            Asset asset = assetRepository.findById(request.assetId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
            SshCredential credential = sshCredentialRepository.findByAssetId(asset.getId()).orElse(null);
            String secret = null;
            String username = null;
            SshAuthType authType = null;
            List<Long> jumps = List.of();
            if (credential != null) {
                username = credential.getUsername();
                authType = credential.getAuthType();
                secret = credentialCipher.decrypt(credential.getSecretCipher(), credential.getSecretIv());
                jumps = credential.getJumpAssetIds() != null ? credential.getJumpAssetIds() : List.of();
            }
            return new ConnectivityContext(
                    asset.getId(),
                    asset.getHost(),
                    asset.getPort(),
                    username,
                    authType,
                    secret,
                    jumps,
                    readDatabase(asset.getMetadata()));
        }
        return new ConnectivityContext(
                request.assetId(),
                request.host(),
                request.port(),
                request.username(),
                request.authType(),
                request.secret(),
                request.jumpAssetIds(),
                request.database());
    }

    private String readDatabase(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode database = node.get("database");
            return database != null && !database.isNull() ? database.asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
