package com.archops.asset.service;

import com.archops.asset.domain.Asset;
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

    public AssetService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            AuditService auditService,
            AssetTypeRegistry assetTypeRegistry) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.auditService = auditService;
        this.assetTypeRegistry = assetTypeRegistry;
    }

    @Transactional
    public AssetResponse create(AssetRequest request, Long actorId, String actorName) {
        assetTypeRegistry.findRequired(request.kind().name()).validateCreate(request);
        Asset asset = new Asset();
        asset.setName(request.name());
        asset.setKind(request.kind());
        asset.setHost(request.host());
        asset.setPort(request.port());
        asset.setMetadata(request.metadata() != null ? request.metadata() : "{}");
        asset.setParentId(request.parentId());
        asset.setEnabled(request.enabled() == null || request.enabled());
        asset = assetRepository.save(asset);
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
        asset.setName(request.name());
        asset.setKind(request.kind());
        asset.setHost(request.host());
        asset.setPort(request.port());
        asset.setMetadata(request.metadata() != null ? request.metadata() : "{}");
        asset.setParentId(request.parentId());
        if (request.enabled() != null) {
            asset.setEnabled(request.enabled());
        }
        asset = assetRepository.save(asset);
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

    private Asset findAssetOrThrow(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
    }

    private AssetResponse toResponse(Asset asset) {
        boolean hasCred = sshCredentialRepository.findByAssetId(asset.getId()).isPresent();
        return new AssetResponse(
                asset.getId(),
                asset.getName(),
                asset.getKind(),
                asset.getHost(),
                asset.getPort(),
                asset.getMetadata(),
                asset.getParentId(),
                asset.isEnabled(),
                hasCred,
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }
}
