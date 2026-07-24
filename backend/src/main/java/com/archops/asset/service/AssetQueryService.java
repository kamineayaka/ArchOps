package com.archops.asset.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.SshAuthType;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.AssetQueryResponse;
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

@Service
public class AssetQueryService {

    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final CredentialCipher credentialCipher;
    private final AssetTypeRegistry assetTypeRegistry;
    private final ObjectMapper objectMapper;

    public AssetQueryService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            AssetTypeRegistry assetTypeRegistry,
            ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.assetTypeRegistry = assetTypeRegistry;
        this.objectMapper = objectMapper;
    }

    public AssetQueryResponse query(Long assetId, String statement) {
        if (!StringUtils.hasText(statement)) {
            return AssetQueryResponse.failure("请输入查询语句");
        }
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
        AssetTypeHandler handler = assetTypeRegistry.findRequired(asset.getKind().name());
        ConnectivityContext ctx = buildContext(asset);
        return handler.executeReadonlyQuery(ctx, statement.trim());
    }

    private ConnectivityContext buildContext(Asset asset) {
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
                readMeta(asset.getMetadata(), "database"),
                readMeta(asset.getMetadata(), "k8sMode"),
                readMeta(asset.getMetadata(), "apiServerUrl"));
    }

    private String readMeta(String metadata, String field) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode value = node.get(field);
            return value != null && !value.isNull() ? value.asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
