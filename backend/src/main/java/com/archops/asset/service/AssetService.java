package com.archops.asset.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
    private final ObjectMapper objectMapper;

    public AssetService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list() {
        return assetRepository.findByDeletedAtIsNull().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse get(Long id) {
        return toResponse(findAssetOrThrow(id));
    }

    @Transactional
    public void delete(Long id, Long actorId, String actorName) {
        Asset asset = findAssetOrThrow(id);
        Instant now = Instant.now();
        asset.setDeletedAt(now);
        asset.setDeletedBy(actorId);
        asset.setDeleteReason("api.delete");
        asset.setEnabled(false);
        assetRepository.save(asset);
        sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(id).ifPresent(cred -> {
            cred.setDeletedAt(now);
            cred.setDeletedBy(actorId);
            sshCredentialRepository.save(cred);
        });
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset.soft_delete", "asset:" + id,
                "MEDIUM", "SUCCESS", "{\"elementId\":\"" + asset.getElementId() + "\"}", null, null));
    }

    @Transactional(readOnly = true)
    public SshCredential getSshCredential(Long assetId) {
        return sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND", "该资产未配置 SSH 凭证"));
    }

    public String decryptSecret(SshCredential credential) {
        return credentialCipher.decrypt(credential.getSecretCipher(), credential.getSecretIv());
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

    private Asset findAssetOrThrow(Long id) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
    }

    private AssetResponse toResponse(Asset asset) {
        boolean hasCred = sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).isPresent();
        return new AssetResponse(
                asset.getId(),
                asset.getElementId(),
                asset.getName(),
                asset.getKind(),
                asset.getHost(),
                asset.getPort(),
                asset.getMetadata(),
                readDescription(asset.getMetadata()),
                asset.isEnabled(),
                hasCred,
                asset.getDeletedAt(),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }
}
