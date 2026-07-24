package com.archops.asset.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.SshAuthType;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.dto.SshCredentialRequest;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.asset.type.AssetTypeRegistry;
import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.archops.common.security.CredentialCipher.EncryptedSecret;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final CredentialCipher credentialCipher;
    private final AuditService auditService;
    private final AssetTypeRegistry assetTypeRegistry;
    private final AssetGroupService assetGroupService;
    private final ObjectMapper objectMapper;

    public AssetService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            AuditService auditService,
            AssetTypeRegistry assetTypeRegistry,
            AssetGroupService assetGroupService,
            ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.auditService = auditService;
        this.assetTypeRegistry = assetTypeRegistry;
        this.assetGroupService = assetGroupService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssetResponse create(AssetRequest request, Long actorId, String actorName) {
        assetTypeRegistry.findRequired(request.kind().name()).validateCreate(request);
        Asset asset = new Asset();
        applyRequest(asset, request);
        asset = assetRepository.save(asset);

        if (hasCredentialPayload(request)) {
            saveSshCredential(asset.getId(), toCredentialRequest(request), actorId, actorName);
        }
        if (request.groupId() != null) {
            assetGroupService.addMembers(request.groupId(), List.of(asset.getId()), actorId, actorName);
        }

        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset.create", "asset:" + asset.getId(),
                "LOW", "SUCCESS", "{\"name\":\"" + asset.getName() + "\"}", null, null));
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list() {
        return assetRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse get(Long id) {
        return toResponse(findAssetOrThrow(id));
    }

    @Transactional
    public AssetResponse update(Long id, AssetRequest request, Long actorId, String actorName) {
        assetTypeRegistry.findRequired(request.kind().name()).validateUpdate(request);
        Asset asset = findAssetOrThrow(id);
        applyRequest(asset, request);
        asset = assetRepository.save(asset);

        if (hasCredentialPayload(request)) {
            saveSshCredential(id, toCredentialRequest(request), actorId, actorName);
        }
        if (request.groupId() != null) {
            assetGroupService.addMembers(request.groupId(), List.of(id), actorId, actorName);
        }

        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset.update", "asset:" + asset.getId(),
                "LOW", "SUCCESS", null, null, null));
        return toResponse(asset);
    }

    @Transactional
    public void delete(Long id, Long actorId, String actorName) {
        Asset asset = findAssetOrThrow(id);
        assetRepository.delete(asset);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset.delete", "asset:" + id,
                "MEDIUM", "SUCCESS", null, null, null));
    }

    @Transactional
    public void saveSshCredential(Long assetId, SshCredentialRequest request, Long actorId, String actorName) {
        findAssetOrThrow(assetId);
        EncryptedSecret encrypted = credentialCipher.encrypt(request.secret());
        SshCredential credential = sshCredentialRepository.findByAssetId(assetId)
                .orElseGet(SshCredential::new);
        credential.setAssetId(assetId);
        credential.setUsername(request.username());
        credential.setAuthType(request.authType());
        credential.setSecretCipher(encrypted.cipher());
        credential.setSecretIv(encrypted.iv());
        credential.setJumpAssetIds(request.jumpAssetIds() != null ? request.jumpAssetIds() : List.of());
        sshCredentialRepository.save(credential);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset.credential.save", "asset:" + assetId,
                "HIGH", "SUCCESS", null, null, null));
    }

    @Transactional(readOnly = true)
    public SshCredential getSshCredential(Long assetId) {
        return sshCredentialRepository.findByAssetId(assetId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND", "该资产未配置 SSH 凭证"));
    }

    public String decryptSecret(SshCredential credential) {
        return credentialCipher.decrypt(credential.getSecretCipher(), credential.getSecretIv());
    }

    private void applyRequest(Asset asset, AssetRequest request) {
        asset.setName(request.name() != null ? request.name().trim() : request.name());
        asset.setKind(request.kind());
        String host = request.host();
        asset.setHost(host != null && !host.isBlank() ? host.trim() : host);
        asset.setPort(request.port());
        asset.setMetadata(mergeMetadata(
                request.metadata(),
                request.description(),
                request.database(),
                request.k8sMode(),
                request.apiServerUrl()));
        asset.setParentId(request.parentId());
        if (request.enabled() != null) {
            asset.setEnabled(request.enabled());
        } else if (asset.getId() == null) {
            asset.setEnabled(true);
        }
    }

    private String mergeMetadata(
            String metadata,
            String description,
            String database,
            String k8sMode,
            String apiServerUrl) {
        try {
            ObjectNode node;
            if (metadata != null && !metadata.isBlank()) {
                JsonNode parsed = objectMapper.readTree(metadata);
                node = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            } else {
                node = objectMapper.createObjectNode();
            }
            if (description != null) {
                String trimmed = description.trim();
                if (trimmed.isEmpty()) {
                    node.remove("description");
                } else {
                    // Notes only — never Architecture SSOT.
                    node.put("description", trimmed);
                }
            }
            putOptional(node, "database", database);
            putOptional(node, "k8sMode", k8sMode);
            putOptional(node, "apiServerUrl", apiServerUrl);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_METADATA_INVALID", "资产 metadata 无效");
        }
    }

    private static void putOptional(ObjectNode node, String key, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            node.remove(key);
        } else {
            node.put(key, trimmed);
        }
    }

    private String readDescription(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode description = node.get("description");
            return description != null && !description.isNull() ? description.asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasCredentialPayload(AssetRequest request) {
        return request.secret() != null
                && !request.secret().isBlank()
                && request.username() != null
                && !request.username().isBlank();
    }

    private static SshCredentialRequest toCredentialRequest(AssetRequest request) {
        SshAuthType authType = request.authType() != null ? request.authType() : SshAuthType.PASSWORD;
        return new SshCredentialRequest(
                request.username().trim(),
                authType,
                request.secret(),
                request.jumpAssetIds() != null ? request.jumpAssetIds() : List.of());
    }

    private Asset findAssetOrThrow(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
    }

    private AssetResponse toResponse(Asset asset) {
        var credential = sshCredentialRepository.findByAssetId(asset.getId());
        boolean hasCred = credential.isPresent();
        List<Long> jumpIds = credential
                .map(SshCredential::getJumpAssetIds)
                .orElse(List.of());
        return new AssetResponse(
                asset.getId(),
                asset.getName(),
                asset.getKind(),
                asset.getHost(),
                asset.getPort(),
                asset.getMetadata(),
                readDescription(asset.getMetadata()),
                asset.getParentId(),
                asset.isEnabled(),
                hasCred,
                jumpIds != null ? jumpIds : List.of(),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }
}
