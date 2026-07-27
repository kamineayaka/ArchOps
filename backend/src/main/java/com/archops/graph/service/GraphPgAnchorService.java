package com.archops.graph.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.CredentialStaging;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.CredentialStagingRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.common.exception.BusinessException;
import com.archops.graph.changeset.GraphChangeSet;
import com.archops.graph.changeset.GraphChangeSet.GraphOp;
import com.archops.graph.changeset.GraphChangeSet.PgSideEffect;
import com.archops.graph.domain.GraphLabels;
import com.archops.knowledge.architecture.PartitionKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** PG-side anchors and side effects for graph merge (assets + credentials). */
@Service
public class GraphPgAnchorService {

    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final CredentialStagingRepository credentialStagingRepository;
    private final ObjectMapper objectMapper;

    public GraphPgAnchorService(
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            CredentialStagingRepository credentialStagingRepository,
            ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.credentialStagingRepository = credentialStagingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void prepareNodeCreates(List<GraphOp> ops, GraphTempBinder binder) {
        for (GraphOp op : ops) {
            if (!"NODE_CREATE".equalsIgnoreCase(safeOp(op))) {
                continue;
            }
            Map<String, Object> props = op.properties() != null ? op.properties() : Map.of();
            AssetKind kind = parseKind(props.get("kind"), op.labels());
            UUID elementId = parseElementId(props.get("elementId"));
            if (elementId == null) {
                elementId = UUID.randomUUID();
            }
            if (assetRepository.findByElementId(elementId).isPresent()) {
                throw new BusinessException(
                        HttpStatus.CONFLICT, "ELEMENT_ID_EXISTS", "elementId 已存在: " + elementId);
            }

            Asset asset = new Asset();
            asset.setElementId(elementId);
            asset.setKind(kind);
            asset.setName(requireName(props.get("name")));
            asset.setHost(asString(props.get("host")));
            asset.setPort(asInteger(props.get("port")));
            asset.setEnabled(props.get("enabled") == null || Boolean.TRUE.equals(props.get("enabled")));
            asset.setMetadata(buildMetadata(props, kind));
            asset = assetRepository.save(asset);

            if (op.tempId() != null && !op.tempId().isBlank()) {
                binder.bind(op.tempId(), asset.getElementId(), asset.getId());
            } else {
                binder.remember(asset.getElementId(), asset.getId());
            }
        }
    }

    @Transactional
    public void applySideEffects(List<PgSideEffect> effects, GraphTempBinder binder, Long actorId, Long proposalId) {
        if (effects == null) {
            return;
        }
        Instant now = Instant.now();
        for (PgSideEffect effect : effects) {
            if (effect == null || effect.effect() == null) {
                continue;
            }
            String type = effect.effect().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case "CREDENTIAL_UPSERT_REF" -> consumeCredentialStaging(effect, binder, actorId, proposalId, now);
                case "CREDENTIAL_SOFT_DELETE" -> softDeleteCredential(effect, binder, actorId, now);
                case "ASSET_SOFT_DELETE" -> softDeleteAsset(effect, binder, actorId, now, "pgSideEffect");
                case "ASSET_ROW_ENSURE" -> {
                    // no-op: NODE_CREATE already ensures rows
                }
                default -> throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PG_SIDE_EFFECT_UNKNOWN", "未知 pgSideEffect: " + effect.effect());
            }
        }
    }

    @Transactional
    public void applyNodeSoftDeletes(List<GraphOp> ops, GraphTempBinder binder, Long actorId) {
        Instant now = Instant.now();
        for (GraphOp op : ops) {
            if (!"NODE_SOFT_DELETE".equalsIgnoreCase(safeOp(op))) {
                continue;
            }
            UUID elementId = binder.resolveElementId(op.ref());
            Asset asset = assetRepository.findByElementId(elementId)
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在: " + elementId));
            if (asset.getDeletedAt() == null) {
                asset.setDeletedAt(now);
                asset.setDeletedBy(actorId);
                asset.setDeleteReason(op.reason() != null ? op.reason() : "graph.NODE_SOFT_DELETE");
                asset.setEnabled(false);
                assetRepository.save(asset);
            }
            sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).ifPresent(cred -> {
                cred.setDeletedAt(now);
                cred.setDeletedBy(actorId);
                sshCredentialRepository.save(cred);
            });
        }
    }

    @Transactional
    public void syncProjections(List<GraphOp> ops, GraphTempBinder binder) {
        for (GraphOp op : ops) {
            if (!"NODE_UPDATE".equalsIgnoreCase(safeOp(op))) {
                continue;
            }
            UUID elementId = binder.resolveElementId(op.ref());
            Asset asset = assetRepository.findByElementIdAndDeletedAtIsNull(elementId).orElse(null);
            if (asset == null) {
                continue;
            }
            Map<String, Object> set = op.set() != null ? op.set() : Map.of();
            if (set.containsKey("name") && set.get("name") != null) {
                asset.setName(String.valueOf(set.get("name")).trim());
            }
            if (set.containsKey("host")) {
                Object host = set.get("host");
                asset.setHost(host == null ? null : String.valueOf(host));
            }
            if (set.containsKey("port")) {
                asset.setPort(asInteger(set.get("port")));
            }
            if (set.containsKey("enabled")) {
                asset.setEnabled(Boolean.TRUE.equals(set.get("enabled")));
            }
            if (set.containsKey("metadata") || set.containsKey("slug") || set.containsKey("description")) {
                asset.setMetadata(mergeMetadataUpdate(asset.getMetadata(), set, asset.getKind()));
            }
            asset.setGraphSyncedAt(Instant.now());
            assetRepository.save(asset);
        }
    }

    private void consumeCredentialStaging(
            PgSideEffect effect, GraphTempBinder binder, Long actorId, Long proposalId, Instant now) {
        if (effect.credentialStagingId() == null || effect.credentialStagingId().isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "STAGING_ID_REQUIRED", "credentialStagingId 不能为空");
        }
        UUID stagingId = UUID.fromString(effect.credentialStagingId().trim());
        CredentialStaging staging = credentialStagingRepository
                .findByIdAndConsumedAtIsNull(stagingId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "STAGING_NOT_FOUND", "凭证暂存不存在或已消费"));
        if (staging.getExpiresAt().isBefore(now)) {
            throw new BusinessException(HttpStatus.GONE, "STAGING_EXPIRED", "凭证暂存已过期");
        }
        Long assetId = effect.pgAssetId();
        if (assetId == null && effect.tempId() != null) {
            assetId = binder.requireTemp(effect.tempId()).pgAssetId();
        }
        if (assetId == null && staging.getAssetId() != null) {
            assetId = staging.getAssetId();
        }
        if (assetId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_ID_REQUIRED", "无法解析凭证所属资产");
        }

        sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(assetId).ifPresent(old -> {
            old.setDeletedAt(now);
            old.setDeletedBy(actorId);
            sshCredentialRepository.save(old);
        });

        SshCredential credential = new SshCredential();
        credential.setAssetId(assetId);
        credential.setUsername(staging.getUsername());
        credential.setAuthType(staging.getAuthType());
        credential.setSecretCipher(staging.getSecretCipher());
        credential.setSecretIv(staging.getSecretIv());
        credential.setPassphraseHash(staging.getPassphraseHash());
        credential.setJumpAssetIds(List.of());
        sshCredentialRepository.save(credential);

        staging.setConsumedAt(now);
        staging.setProposalId(proposalId);
        staging.setAssetId(assetId);
        credentialStagingRepository.save(staging);
    }

    private void softDeleteCredential(PgSideEffect effect, GraphTempBinder binder, Long actorId, Instant now) {
        Long assetId = effect.pgAssetId();
        if (assetId == null && effect.tempId() != null) {
            assetId = binder.requireTemp(effect.tempId()).pgAssetId();
        }
        if (assetId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_ID_REQUIRED", "无法解析凭证所属资产");
        }
        Long finalAssetId = assetId;
        sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(finalAssetId).ifPresent(cred -> {
            cred.setDeletedAt(now);
            cred.setDeletedBy(actorId);
            sshCredentialRepository.save(cred);
        });
    }

    private void softDeleteAsset(
            PgSideEffect effect, GraphTempBinder binder, Long actorId, Instant now, String reason) {
        Long assetId = effect.pgAssetId();
        if (assetId == null && effect.tempId() != null) {
            assetId = binder.requireTemp(effect.tempId()).pgAssetId();
        }
        if (assetId == null) {
            return;
        }
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null || asset.getDeletedAt() != null) {
            return;
        }
        asset.setDeletedAt(now);
        asset.setDeletedBy(actorId);
        asset.setDeleteReason(reason);
        asset.setEnabled(false);
        assetRepository.save(asset);
    }

    private String buildMetadata(Map<String, Object> props, AssetKind kind) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            Object rawMeta = props.get("metadata");
            if (rawMeta instanceof Map<?, ?> map) {
                node = objectMapper.valueToTree(map);
            } else if (rawMeta instanceof String s && StringUtils.hasText(s)) {
                JsonNode parsed = objectMapper.readTree(s);
                if (parsed.isObject()) {
                    node = (ObjectNode) parsed;
                }
            }
            if (kind == AssetKind.TAG) {
                Object slug = props.get("slug");
                if (slug == null) {
                    slug = node.get("slug") != null ? node.get("slug").asText(null) : null;
                }
                if (slug == null || String.valueOf(slug).isBlank()) {
                    slug = PartitionKeys.normalizeSlug(String.valueOf(props.get("name")));
                } else {
                    slug = PartitionKeys.normalizeSlug(String.valueOf(slug));
                }
                node.put("slug", String.valueOf(slug));
            }
            if (props.get("description") != null) {
                node.put("description", String.valueOf(props.get("description")));
            }
            return objectMapper.writeValueAsString(node);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_METADATA_INVALID", "metadata 无效");
        }
    }

    private String mergeMetadataUpdate(String current, Map<String, Object> set, AssetKind kind) {
        try {
            ObjectNode node;
            if (StringUtils.hasText(current)) {
                JsonNode parsed = objectMapper.readTree(current);
                node = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            } else {
                node = objectMapper.createObjectNode();
            }
            if (set.get("metadata") instanceof Map<?, ?> map) {
                node = objectMapper.valueToTree(map);
            }
            if (set.containsKey("description")) {
                Object d = set.get("description");
                if (d == null || String.valueOf(d).isBlank()) {
                    node.remove("description");
                } else {
                    node.put("description", String.valueOf(d));
                }
            }
            if (kind == AssetKind.TAG && set.containsKey("slug")) {
                node.put("slug", PartitionKeys.normalizeSlug(String.valueOf(set.get("slug"))));
            }
            return objectMapper.writeValueAsString(node);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_METADATA_INVALID", "metadata 无效");
        }
    }

    private static AssetKind parseKind(Object kindObj, List<String> labels) {
        if (kindObj != null && StringUtils.hasText(String.valueOf(kindObj))) {
            try {
                return AssetKind.valueOf(String.valueOf(kindObj).trim().toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_KIND_INVALID", "非法 kind: " + kindObj);
            }
        }
        if (labels != null) {
            for (AssetKind kind : AssetKind.values()) {
                String spec = GraphLabels.specialization(kind);
                for (String label : labels) {
                    if (label != null && spec.equalsIgnoreCase(label)) {
                        return kind;
                    }
                }
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_KIND_REQUIRED", "NODE_CREATE 需要 kind");
    }

    private static UUID parseElementId(Object value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(String.valueOf(value).trim());
    }

    private static String requireName(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NAME_REQUIRED", "name 不能为空");
        }
        return String.valueOf(value).trim();
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    private static String safeOp(GraphOp op) {
        return op != null && op.op() != null ? op.op().trim() : "";
    }
}
