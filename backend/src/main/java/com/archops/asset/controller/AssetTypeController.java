package com.archops.asset.controller;

import com.archops.asset.type.AssetTypeDescriptor;
import com.archops.asset.type.AssetTypeRegistry;
import com.archops.common.dto.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asset-types")
public class AssetTypeController {

    private final AssetTypeRegistry assetTypeRegistry;

    public AssetTypeController(AssetTypeRegistry assetTypeRegistry) {
        this.assetTypeRegistry = assetTypeRegistry;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<AssetTypeDescriptor>> list() {
        return ApiResponse.ok(assetTypeRegistry.descriptors());
    }
}
