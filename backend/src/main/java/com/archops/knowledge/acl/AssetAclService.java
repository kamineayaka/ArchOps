package com.archops.knowledge.acl;

import com.archops.asset.repository.AssetGroupMemberRepository;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.retrieval.RagScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Asset-scoped ACL backed by {@code user_assets}. ADMIN sees all assets.
 */
@Service
public class AssetAclService {

    private final UserAssetRepository userAssetRepository;
    private final AssetGroupMemberRepository groupMemberRepository;

    public AssetAclService(
            UserAssetRepository userAssetRepository, AssetGroupMemberRepository groupMemberRepository) {
        this.userAssetRepository = userAssetRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public boolean isAdmin(Collection<String> roles) {
        return roles != null && roles.stream().anyMatch(r -> {
            String n = normalizeRole(r);
            return "ADMIN".equals(n) || "ROLE_ADMIN".equals(n);
        });
    }

    public boolean canAccessAsset(Long userId, Collection<String> roles, Long assetId) {
        if (assetId == null) {
            return false;
        }
        if (isAdmin(roles)) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        return userAssetRepository.existsByUserIdAndAssetId(userId, assetId);
    }

    public List<Long> filterAssetIds(Long userId, Collection<String> roles, Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        if (isAdmin(roles)) {
            return List.copyOf(assetIds);
        }
        if (userId == null) {
            return List.of();
        }
        Set<Long> wanted = assetIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return List.of();
        }
        return userAssetRepository.findAssetIdsByUserIdAndAssetIdIn(userId, wanted);
    }

    public List<Long> allowedAssetIds(Long userId, Collection<String> roles) {
        if (isAdmin(roles)) {
            return null; // null = unrestricted
        }
        if (userId == null) {
            return List.of();
        }
        return userAssetRepository.findAssetIdsByUserId(userId);
    }

    public boolean canAccessPartition(Long userId, Collection<String> roles, String partitionKey) {
        PartitionKeys.validate(partitionKey);
        if (isAdmin(roles)) {
            return true;
        }
        if (PartitionKeys.GLOBAL.equals(partitionKey)) {
            return userId != null;
        }
        if (partitionKey.startsWith("asset:")) {
            Long assetId = Long.parseLong(partitionKey.substring("asset:".length()));
            return canAccessAsset(userId, roles, assetId);
        }
        if (partitionKey.startsWith("group:")) {
            Long groupId = Long.parseLong(partitionKey.substring("group:".length()));
            List<Long> memberIds = groupMemberRepository.findByIdGroupId(groupId).stream()
                    .map(m -> m.getAssetId())
                    .toList();
            if (memberIds.isEmpty()) {
                return false;
            }
            List<Long> allowed = filterAssetIds(userId, roles, memberIds);
            return !allowed.isEmpty();
        }
        return false;
    }

    public void requirePartitionAccess(Long userId, Collection<String> roles, String partitionKey) {
        if (!canAccessPartition(userId, roles, partitionKey)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PARTITION_ACCESS_DENIED",
                    "无权访问分区: " + partitionKey);
        }
    }

    /**
     * Intersect a RAG scope with the caller's allowed assets/groups.
     * ADMIN returns scope unchanged. Null/empty scope stays empty (no extra filter).
     */
    public RagScope intersectScope(Long userId, Collection<String> roles, RagScope scope) {
        if (scope == null || scope.isEmpty() || isAdmin(roles)) {
            return scope;
        }
        List<Long> allowedAssets = allowedAssetIds(userId, roles);
        Set<Long> allowedSet = allowedAssets == null ? Set.of() : new HashSet<>(allowedAssets);

        List<Long> assetIds = new ArrayList<>();
        if (scope.assetIds() != null) {
            for (Long id : scope.assetIds()) {
                if (allowedSet.contains(id)) {
                    assetIds.add(id);
                }
            }
        }

        List<Long> groupIds = new ArrayList<>();
        if (scope.groupIds() != null) {
            for (Long groupId : scope.groupIds()) {
                List<Long> members = groupMemberRepository.findByIdGroupId(groupId).stream()
                        .map(m -> m.getAssetId())
                        .toList();
                if (members.stream().anyMatch(allowedSet::contains)) {
                    groupIds.add(groupId);
                }
            }
        }

        List<String> partitionKeys = new ArrayList<>();
        if (scope.partitionKeys() != null) {
            for (String key : scope.partitionKeys()) {
                if (canAccessPartition(userId, roles, key)) {
                    partitionKeys.add(key);
                }
            }
        }

        return new RagScope(assetIds, groupIds, partitionKeys);
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
