package com.archops.asset.controller;

import com.archops.asset.dto.AssetResponse;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.service.AssetConnectionTestService;
import com.archops.asset.service.AssetService;
import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import java.util.List;
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

    public AssetController(AssetService assetService, AssetConnectionTestService connectionTestService) {
        this.assetService = assetService;
        this.connectionTestService = connectionTestService;
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        assetService.delete(id, principal.getUserId(), principal.getUsername());
        return ApiResponse.ok("资产已删除", null);
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<TestConnectionResponse> testSavedConnection(@PathVariable Long id) {
        return ApiResponse.ok(connectionTestService.test(new TestConnectionRequest(id)));
    }
}
