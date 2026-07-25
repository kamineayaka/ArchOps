package com.archops.asset.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetGroup;
import com.archops.asset.domain.AssetGroupMember;
import com.archops.asset.dto.AssetGroupRequest;
import com.archops.asset.dto.AssetGroupResponse;
import com.archops.asset.dto.AssetGroupResponse.AssetMemberSummary;
import com.archops.asset.repository.AssetGroupMemberRepository;
import com.archops.asset.repository.AssetGroupRepository;
import com.archops.asset.repository.AssetRepository;
import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetGroupService {

    private final AssetGroupRepository assetGroupRepository;
    private final AssetGroupMemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final AuditService auditService;

    public AssetGroupService(
            AssetGroupRepository assetGroupRepository,
            AssetGroupMemberRepository memberRepository,
            AssetRepository assetRepository,
            AuditService auditService) {
        this.assetGroupRepository = assetGroupRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AssetGroupResponse create(AssetGroupRequest request, Long actorId, String actorName) {
        String name = request.name().trim();
        if (assetGroupRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSET_GROUP_NAME_EXISTS", "资产组名称已存在");
        }
        AssetGroup group = new AssetGroup();
        group.setName(name);
        group.setDescription(request.description());
        group.setEnabled(request.enabled() == null || request.enabled());
        group = assetGroupRepository.save(group);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset_group.create", "asset_group:" + group.getId(),
                "LOW", "SUCCESS", "{\"name\":\"" + group.getName() + "\"}", null, null));
        return toResponse(group, List.of(), true);
    }

    /** 按名称查找（忽略大小写）；不存在则创建。用于资产表单「输入即新建分组」。 */
    @Transactional
    public AssetGroupResponse findOrCreate(String rawName, Long actorId, String actorName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "分组名称不能为空");
        }
        return assetGroupRepository
                .findByNameIgnoreCase(name)
                .map(g -> toResponse(g, memberRepository.findByIdGroupId(g.getId()), false))
                .orElseGet(() -> create(new AssetGroupRequest(name, null, true), actorId, actorName));
    }

    @Transactional(readOnly = true)
    public List<AssetGroupResponse> list() {
        return assetGroupRepository.findAll().stream()
                .map(g -> toResponse(g, memberRepository.findByIdGroupId(g.getId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetGroupResponse get(Long id) {
        AssetGroup group = findGroupOrThrow(id);
        return toResponse(group, memberRepository.findByIdGroupId(id), true);
    }

    @Transactional
    public AssetGroupResponse update(Long id, AssetGroupRequest request, Long actorId, String actorName) {
        AssetGroup group = findGroupOrThrow(id);
        String name = request.name().trim();
        if (assetGroupRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSET_GROUP_NAME_EXISTS", "资产组名称已存在");
        }
        group.setName(name);
        group.setDescription(request.description());
        if (request.enabled() != null) {
            group.setEnabled(request.enabled());
        }
        group = assetGroupRepository.save(group);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset_group.update", "asset_group:" + id,
                "LOW", "SUCCESS", "{\"name\":\"" + group.getName() + "\"}", null, null));
        return toResponse(group, memberRepository.findByIdGroupId(id), true);
    }

    @Transactional
    public void delete(Long id, Long actorId, String actorName) {
        AssetGroup group = findGroupOrThrow(id);
        // Membership rows cascade; assets are intentionally preserved.
        assetGroupRepository.delete(group);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset_group.delete", "asset_group:" + id,
                "LOW", "SUCCESS", "{\"name\":\"" + group.getName() + "\"}", null, null));
    }

    @Transactional
    public AssetGroupResponse replaceMembers(Long groupId, List<Long> assetIds, Long actorId, String actorName) {
        findGroupOrThrow(groupId);
        List<Long> normalized = normalizeAssetIds(assetIds);
        validateAssetsExist(normalized);
        memberRepository.deleteByGroupId(groupId);
        for (Long assetId : normalized) {
            memberRepository.save(new AssetGroupMember(groupId, assetId));
        }
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset_group.members.replace", "asset_group:" + groupId,
                "LOW", "SUCCESS", "{\"memberCount\":" + normalized.size() + "}", null, null));
        return get(groupId);
    }

    @Transactional
    public AssetGroupResponse addMembers(Long groupId, List<Long> assetIds, Long actorId, String actorName) {
        findGroupOrThrow(groupId);
        List<Long> normalized = normalizeAssetIds(assetIds);
        validateAssetsExist(normalized);
        Set<Long> existing = memberRepository.findByIdGroupId(groupId).stream()
                .map(AssetGroupMember::getAssetId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long assetId : normalized) {
            if (existing.add(assetId)) {
                memberRepository.save(new AssetGroupMember(groupId, assetId));
            }
        }
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset_group.members.add", "asset_group:" + groupId,
                "LOW", "SUCCESS", "{\"added\":" + normalized.size() + "}", null, null));
        return get(groupId);
    }

    @Transactional
    public AssetGroupResponse removeMember(Long groupId, Long assetId, Long actorId, String actorName) {
        findGroupOrThrow(groupId);
        memberRepository.deleteByGroupIdAndAssetId(groupId, assetId);
        auditService.record(new AuditService.AuditEntry(
                actorId, actorName, "asset_group.members.remove", "asset_group:" + groupId,
                "LOW", "SUCCESS", "{\"assetId\":" + assetId + "}", null, null));
        return get(groupId);
    }

    @Transactional(readOnly = true)
    public List<Long> resolveMemberAssetIds(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        for (Long groupId : groupIds) {
            findGroupOrThrow(groupId);
        }
        return memberRepository.findByIdGroupIdIn(groupIds).stream()
                .map(AssetGroupMember::getAssetId)
                .distinct()
                .toList();
    }

    private AssetGroup findGroupOrThrow(Long id) {
        return assetGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ASSET_GROUP_NOT_FOUND", "资产组不存在"));
    }

    private List<Long> normalizeAssetIds(List<Long> assetIds) {
        if (assetIds == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(assetIds));
    }

    private void validateAssetsExist(List<Long> assetIds) {
        for (Long assetId : assetIds) {
            if (!assetRepository.existsById(assetId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_NOT_FOUND", "资产不存在: " + assetId);
            }
        }
    }

    private AssetGroupResponse toResponse(AssetGroup group, List<AssetGroupMember> members, boolean includeMembers) {
        List<AssetMemberSummary> summaries = List.of();
        if (includeMembers && !members.isEmpty()) {
            Map<Long, Asset> assets = assetRepository.findAllById(
                            members.stream().map(AssetGroupMember::getAssetId).toList())
                    .stream()
                    .collect(Collectors.toMap(Asset::getId, Function.identity()));
            summaries = members.stream()
                    .map(m -> {
                        Asset asset = assets.get(m.getAssetId());
                        if (asset == null) {
                            return new AssetMemberSummary(m.getAssetId(), "?", "UNKNOWN", null);
                        }
                        return new AssetMemberSummary(
                                asset.getId(),
                                asset.getName(),
                                asset.getKind().name(),
                                asset.getHost());
                    })
                    .toList();
        }
        int count = includeMembers ? summaries.size() : (int) memberRepository.countByIdGroupId(group.getId());
        if (!includeMembers && members != null && !members.isEmpty()) {
            count = members.size();
        }
        return new AssetGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.isEnabled(),
                count,
                includeMembers ? summaries : List.of(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }
}
