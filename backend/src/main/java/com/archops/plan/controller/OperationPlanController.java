package com.archops.plan.controller;

import com.archops.common.api.ApiResponse;
import com.archops.common.api.BranchSelectionResult;
import com.archops.plan.dto.OperationPlanResponse;
import com.archops.plan.dto.SelectBranchRequest;
import com.archops.plan.dto.StartExecutionResponse;
import com.archops.plan.service.BranchSelectionService;
import com.archops.plan.service.OperationPlanService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operation plan review + shared branch-selection gate.
 * 诊断选支, 批准并冻结, and 执行 are explicit requests and do not require a user identity.
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
    @PreAuthorize("permitAll()")
    public ApiResponse<BranchSelectionResult> selectBranch(
            @PathVariable String conflictId,
            @Valid @RequestBody SelectBranchRequest request
    ) {
        return ApiResponse.ok(branchSelectionService.select(
                conflictId, request.forkId(), request.diagnosisId()));
    }

    @GetMapping("/conflicts/{conflictId}/operation-plans/active")
    @PreAuthorize("permitAll()")
    public ApiResponse<OperationPlanResponse> activePlan(@PathVariable String conflictId) {
        return ApiResponse.ok(operationPlanService.getActive(conflictId));
    }

    @GetMapping("/operation-plans/{planId}")
    @PreAuthorize("permitAll()")
    public ApiResponse<OperationPlanResponse> getPlan(@PathVariable String planId) {
        return ApiResponse.ok(operationPlanService.getById(planId));
    }

    @PostMapping("/operation-plans/{planId}/approve")
    @PreAuthorize("permitAll()")
    public ApiResponse<OperationPlanResponse> approve(@PathVariable String planId) {
        return ApiResponse.ok(operationPlanService.approve(planId));
    }

    @PostMapping("/operation-plans/{planId}/start-execution")
    @PreAuthorize("permitAll()")
    public ApiResponse<StartExecutionResponse> startExecution(@PathVariable String planId) {
        return ApiResponse.ok(operationPlanService.startExecution(planId));
    }
}
