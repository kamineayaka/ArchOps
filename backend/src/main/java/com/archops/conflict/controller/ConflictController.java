package com.archops.conflict.controller;

import com.archops.common.api.ApiResponse;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.dto.OpenOperationPlanResponse;
import com.archops.conflict.service.ConflictCollaborationService;
import com.archops.conflict.service.ConflictDetectionService;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.user.security.AuthUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conflict warn / collaboration HTTP surface.
 */
@RestController
@RequestMapping("/api/conflicts")
@PreAuthorize("isAuthenticated()")
public class ConflictController {

    private final ConflictDetectionService conflictDetectionService;
    private final ConflictCollaborationService conflictCollaborationService;

    public ConflictController(
            ConflictDetectionService conflictDetectionService,
            ConflictCollaborationService conflictCollaborationService
    ) {
        this.conflictDetectionService = conflictDetectionService;
        this.conflictCollaborationService = conflictCollaborationService;
    }

    @GetMapping
    public ApiResponse<List<ConflictCaseResponse>> listOpen() {
        return ApiResponse.ok(conflictDetectionService.listOpen());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConflictCaseResponse> get(@PathVariable String id) {
        return ApiResponse.ok(conflictDetectionService.getById(id));
    }

    @GetMapping("/by-merge-key")
    public ApiResponse<ConflictCaseResponse> byMergeKey(
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "RUNS_ON") CuratedRelationType relationType
    ) {
        return ApiResponse.ok(conflictDetectionService.getOpenByMergeKey(subjectId, relationType));
    }

    /** 一般角色认领未已知悉冲突 → 已接受处理人（含归属）. */
    @PostMapping("/{id}/claim")
    public ApiResponse<ConflictCaseResponse> claim(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.claim(id, principal));
    }

    /** 高级角色已知悉 → 冲突归属（可不设处理人）. */
    @PostMapping("/{id}/acknowledge")
    public ApiResponse<ConflictCaseResponse> acknowledge(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.acknowledge(id, principal));
    }

    /** 高级角色已知悉并自任为已接受处理人. */
    @PostMapping("/{id}/acknowledge-and-self-appoint")
    public ApiResponse<ConflictCaseResponse> acknowledgeAndSelfAppoint(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.acknowledgeAndSelfAppoint(id, principal));
    }

    /**
     * Attempt to open an operation plan for this conflict (gate only; plan body is ticket 07).
     */
    @PostMapping("/{id}/operation-plans")
    public ApiResponse<OpenOperationPlanResponse> openOperationPlan(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.openOperationPlan(id, principal));
    }
}
