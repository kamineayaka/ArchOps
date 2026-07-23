package com.archops.knowledge.architecture.controller;

import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.knowledge.architecture.dto.ArchitectureViewResponse;
import com.archops.knowledge.architecture.dto.PartitionDetailResponse;
import com.archops.knowledge.architecture.dto.PartitionSummaryResponse;
import com.archops.knowledge.architecture.dto.RevisionWriteRequest;
import com.archops.knowledge.architecture.dto.RollbackRequest;
import com.archops.knowledge.architecture.service.ArchitecturePartitionService;
import com.archops.knowledge.architecture.service.ArchitectureViewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/architecture/partitions")
public class ArchitectureController {

    private final ArchitecturePartitionService partitionService;
    private final ArchitectureViewService viewService;

    public ArchitectureController(
            ArchitecturePartitionService partitionService, ArchitectureViewService viewService) {
        this.partitionService = partitionService;
        this.viewService = viewService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<PartitionSummaryResponse>> list() {
        return ApiResponse.ok(partitionService.listPartitions());
    }

    @GetMapping("/view")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<ArchitectureViewResponse> view(
            @RequestParam(required = false) List<Long> assetIds,
            @RequestParam(required = false) List<Long> groupIds) {
        return ApiResponse.ok(viewService.buildView(assetIds, groupIds));
    }

    @GetMapping("/{key:.+}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<PartitionDetailResponse> get(@PathVariable("key") String key) {
        return ApiResponse.ok(partitionService.getDetail(key));
    }

    @PutMapping("/{key:.+}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PartitionDetailResponse> write(
            @PathVariable("key") String key,
            @Valid @RequestBody RevisionWriteRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(partitionService.adminWrite(key, request, principal.getUserId()));
    }

    @PostMapping("/{key:.+}/rollback")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PartitionDetailResponse> rollback(
            @PathVariable("key") String key,
            @Valid @RequestBody RollbackRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(partitionService.rollback(key, request.targetVersion(), principal.getUserId()));
    }
}
