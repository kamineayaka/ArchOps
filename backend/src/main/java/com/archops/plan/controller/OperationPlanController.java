package com.archops.plan.controller;

import com.archops.common.api.ApiResponse;
import com.archops.plan.dto.OperationPlanResponse;
import com.archops.plan.dto.SelectBranchRequest;
import com.archops.plan.dto.StartExecutionResponse;
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
 * Operation plan review surface (ticket 07). Diagnosis forks remain read-only on conflict APIs;
 * only the accepted handler may select a branch here.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class OperationPlanController {

    private final OperationPlanService operationPlanService;

    public OperationPlanController(OperationPlanService operationPlanService) {
        this.operationPlanService = operationPlanService;
    }

    @PostMapping("/conflicts/{conflictId}/branch-selection")
    public ApiResponse<OperationPlanResponse> selectBranch(
            @PathVariable String conflictId,
            @Valid @RequestBody SelectBranchRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(operationPlanService.selectBranch(conflictId, request.forkId(), principal));
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
