package com.archops.asset.controller;

import com.archops.asset.dto.AssetResponse;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.service.AssetConnectionTestService;
import com.archops.asset.service.AssetService;
import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.knowledge.acl.AssetAclService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetConnectionTestService connectionTestService;
    private final AssetAclService assetAclService;

    public AssetController(
            AssetService assetService,
            AssetConnectionTestService connectionTestService,
            AssetAclService assetAclService) {
        this.assetService = assetService;
        this.connectionTestService = connectionTestService;
        this.assetAclService = assetAclService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<AssetResponse>> list(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetService.list(principal.getUserId(), principal.roleNames()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<AssetResponse> get(
            @PathVariable Long id, @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetService.get(id, principal.getUserId(), principal.roleNames()));
    }

    /**
     * Direct asset delete is retired under graph SSOT. Soft-delete must go through
     * graph workbench draft → plan → architecture proposal → merge ({@code NODE_SOFT_DELETE}).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        throw new com.archops.common.exception.BusinessException(
                org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED,
                "GRAPH_WRITE_REQUIRED",
                "资产删除须经图工作台草稿 / Proposal 合并（NODE_SOFT_DELETE），禁止直写 PG 以免与 Neo4j 分叉");
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<TestConnectionResponse> testSavedConnection(
            @PathVariable Long id, @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(
                connectionTestService.test(new TestConnectionRequest(id), principal.getUserId(), principal.roleNames()));
    }

    /** ADMIN grants a user visibility to an asset (user_assets). */
    @PostMapping("/{id}/acl/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Map<String, Object>> grantAccess(
            @PathVariable Long id, @PathVariable Long userId) {
        assetService.getUnchecked(id); // ensure asset exists / not deleted
        assetAclService.grant(userId, id);
        return ApiResponse.ok(Map.of("userId", userId, "assetId", id, "granted", true));
    }
}
