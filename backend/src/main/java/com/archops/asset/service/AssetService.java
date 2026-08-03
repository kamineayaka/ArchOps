package com.archops.asset.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.archops.knowledge.acl.AssetAclService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final CredentialCipher credentialCipher;
    private final AssetAclService assetAclService;
    private final ObjectMapper objectMapper;

    public AssetService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialCipher credentialCipher,
            AssetAclService assetAclService,
            ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialCipher = credentialCipher;
        this.assetAclService = assetAclService;
        this.objectMapper = objectMapper;
    }

    /** ACL-aware list for interactive callers. */
    @Transactional(readOnly = true)
    public List<AssetResponse> list(Long userId, Collection<String> roles) {
        List<Asset> assets = assetRepository.findByDeletedAtIsNull();
        if (!assetAclService.isAdmin(roles)) {
            Set<Long> allowed = new HashSet<>(
                    assetAclService.filterAssetIds(
                            userId,
                            roles,
                            assets.stream().map(Asset::getId).toList()));
            assets = assets.stream().filter(a -> allowed.contains(a.getId())).toList();
        }
        Set<Long> withCred = activeCredentialAssetIds(
                assets.stream().map(Asset::getId).collect(Collectors.toSet()));
        return assets.stream().map(asset -> toResponse(asset, withCred.contains(asset.getId()))).toList();
    }

    /** System/inspection path — no ACL. Prefer not to expose via controllers. */
    @Transactional(readOnly = true)
    public List<AssetResponse> listAllForSystem() {
        List<Asset> assets = assetRepository.findByDeletedAtIsNull();
        Set<Long> withCred = activeCredentialAssetIds(
                assets.stream().map(Asset::getId).collect(Collectors.toSet()));
        return assets.stream().map(asset -> toResponse(asset, withCred.contains(asset.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse get(Long id, Long userId, Collection<String> roles) {
        assetAclService.requireAssetAccess(userId, roles, id);
        Asset asset = findAssetOrThrow(id);
        boolean hasCred = sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(id).isPresent();
        return toResponse(asset, hasCred);
    }

    /**
     * Trusted internal read used by graph/SSH plumbing after the caller already checked ACL
     * (or is dialing a jump hop that was pre-authorized).
     */
    @Transactional(readOnly = true)
    public AssetResponse getUnchecked(Long id) {
        Asset asset = findAssetOrThrow(id);
        boolean hasCred = sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(id).isPresent();
        return toResponse(asset, hasCred);
    }

    /**
     * @deprecated Direct PG soft-delete bypasses Neo4j SSOT. Use graph {@code NODE_SOFT_DELETE}
     *             via proposal merge ({@link com.archops.graph.service.GraphPgAnchorService}).
     */
    @Deprecated
    @Transactional
    public void delete(Long id, Long actorId, String actorName) {
        throw new BusinessException(
                HttpStatus.METHOD_NOT_ALLOWED,
                "GRAPH_WRITE_REQUIRED",
                "资产删除须经图工作台草稿 / Proposal 合并（NODE_SOFT_DELETE），禁止直写 PG 以免与 Neo4j 分叉");
    }

    @Transactional(readOnly = true)
    public SshCredential getSshCredential(Long assetId, Long userId, Collection<String> roles) {
        assetAclService.requireAssetAccess(userId, roles, assetId);
        return getSshCredentialUnchecked(assetId);
    }

    @Transactional(readOnly = true)
    public SshCredential getSshCredentialUnchecked(Long assetId) {
        return sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND", "该资产未配置 SSH 凭证"));
    }

    public String decryptSecret(SshCredential credential) {
        return credentialCipher.decrypt(credential.getSecretCipher(), credential.getSecretIv());
    }

    private Set<Long> activeCredentialAssetIds(Set<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(sshCredentialRepository.findAssetIdsWithActiveCredential(assetIds));
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

    private AssetResponse toResponse(Asset asset, boolean hasCred) {
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
