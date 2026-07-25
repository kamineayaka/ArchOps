package com.archops.asset.controller;

import com.archops.asset.dto.AssetGroupMembersRequest;
import com.archops.asset.dto.AssetGroupRequest;
import com.archops.asset.dto.AssetGroupResponse;
import com.archops.asset.service.AssetGroupService;
import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asset-groups")
public class AssetGroupController {

    private final AssetGroupService assetGroupService;

    public AssetGroupController(AssetGroupService assetGroupService) {
        this.assetGroupService = assetGroupService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetGroupResponse> create(
            @Valid @RequestBody AssetGroupRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetGroupService.create(request, principal.getUserId(), principal.getUsername()));
    }

    @PostMapping("/find-or-create")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetGroupResponse> findOrCreate(
            @Valid @RequestBody AssetGroupRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetGroupService.findOrCreate(
                request.name(), principal.getUserId(), principal.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<AssetGroupResponse>> list() {
        return ApiResponse.ok(assetGroupService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<AssetGroupResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(assetGroupService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetGroupResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AssetGroupRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetGroupService.update(id, request, principal.getUserId(), principal.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        assetGroupService.delete(id, principal.getUserId(), principal.getUsername());
        return ApiResponse.ok("资产组已删除", null);
    }

    @PutMapping("/{id}/members")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetGroupResponse> replaceMembers(
            @PathVariable Long id,
            @Valid @RequestBody AssetGroupMembersRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetGroupService.replaceMembers(
                id, request.assetIds(), principal.getUserId(), principal.getUsername()));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetGroupResponse> addMembers(
            @PathVariable Long id,
            @Valid @RequestBody AssetGroupMembersRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetGroupService.addMembers(
                id, request.assetIds(), principal.getUserId(), principal.getUsername()));
    }

    @DeleteMapping("/{id}/members/{assetId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetGroupResponse> removeMember(
            @PathVariable Long id,
            @PathVariable Long assetId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetGroupService.removeMember(
                id, assetId, principal.getUserId(), principal.getUsername()));
    }
}
