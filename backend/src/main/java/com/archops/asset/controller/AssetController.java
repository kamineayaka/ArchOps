package com.archops.asset.controller;

import com.archops.asset.dto.AssetQueryRequest;
import com.archops.asset.dto.AssetQueryResponse;
import com.archops.asset.dto.AssetRequest;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.dto.SshCredentialRequest;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.service.AssetConnectionTestService;
import com.archops.asset.service.AssetQueryService;
import com.archops.asset.service.AssetService;
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
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetConnectionTestService connectionTestService;
    private final AssetQueryService assetQueryService;

    public AssetController(
            AssetService assetService,
            AssetConnectionTestService connectionTestService,
            AssetQueryService assetQueryService) {
        this.assetService = assetService;
        this.connectionTestService = connectionTestService;
        this.assetQueryService = assetQueryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetResponse> create(
            @Valid @RequestBody AssetRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetService.create(request, principal.getUserId(), principal.getUsername()));
    }

    @PostMapping("/test-connection")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<TestConnectionResponse> testConnection(@RequestBody TestConnectionRequest request) {
        return ApiResponse.ok(connectionTestService.test(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<AssetResponse>> list() {
        return ApiResponse.ok(assetService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<AssetResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(assetService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AssetRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(assetService.update(id, request, principal.getUserId(), principal.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        assetService.delete(id, principal.getUserId(), principal.getUsername());
        return ApiResponse.ok("资产已删除", null);
    }

    @PostMapping("/{id}/ssh-credential")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<Void> saveSshCredential(
            @PathVariable Long id,
            @Valid @RequestBody SshCredentialRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        assetService.saveSshCredential(id, request, principal.getUserId(), principal.getUsername());
        return ApiResponse.ok("SSH 凭证已保存", null);
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<TestConnectionResponse> testSavedConnection(@PathVariable Long id) {
        return ApiResponse.ok(connectionTestService.test(new TestConnectionRequest(
                id, null, null, null, null, null, null, null, null, null, null)));
    }

    @PostMapping("/{id}/query")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<AssetQueryResponse> query(
            @PathVariable Long id, @RequestBody AssetQueryRequest request) {
        return ApiResponse.ok(assetQueryService.query(id, request != null ? request.statement() : null));
    }
}
