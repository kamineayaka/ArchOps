package com.archops.plan.controller;

import com.archops.common.api.ApiResponse;
import com.archops.common.api.BranchSelectionResult;
import com.archops.plan.dto.OperationPlanResponse;
import com.archops.plan.dto.SelectBranchRequest;
import com.archops.plan.dto.StartExecutionResponse;
import com.archops.plan.service.BranchSelectionService;
import com.archops.plan.service.OperationPlanService;
import com.archops.user.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operation plan review + shared branch-selection gate (tickets 07 / change-curated 03).
 * Diagnosis GET stays read-only; only the accepted handler may select a branch here.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class OperationPlanController {

    private final OperationPlanService operationPlanService;
    private final BranchSelectionService branchSelectionService;

    public OperationPlanController(
            OperationPlanService operationPlanService,
            BranchSelectionService branchSelectionService
    ) {
        this.operationPlanService = operationPlanService;
        this.branchSelectionService = branchSelectionService;
    }

    @PostMapping("/conflicts/{conflictId}/branch-selection")
    public ApiResponse<BranchSelectionResult> selectBranch(
            @PathVariable String conflictId,
            @Valid @RequestBody SelectBranchRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(branchSelectionService.select(
                conflictId, request.forkId(), request.diagnosisId(), principal));
    }

    @GetMapping("/conflicts/{conflictId}/operation-plans/active")
    public ApiResponse<OperationPlanResponse> activePlan(@PathVariable String conflictId) {
        return ApiResponse.ok(operationPlanService.getActive(conflictId));
    }

    @GetMapping("/operation-plans/{planId}")
    public ApiResponse<OperationPlanResponse> getPlan(@PathVariable String planId) {
        return ApiResponse.ok(operationPlanService.getById(planId));
    }

    @PostMapping("/operation-plans/{planId}/approve")
    public ApiResponse<OperationPlanResponse> approve(
            @PathVariable String planId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(operationPlanService.approve(planId, principal));
    }

    @PostMapping("/operation-plans/{planId}/start-execution")
    public ApiResponse<StartExecutionResponse> startExecution(
            @PathVariable String planId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(operationPlanService.startExecution(planId, principal));
    }
}
