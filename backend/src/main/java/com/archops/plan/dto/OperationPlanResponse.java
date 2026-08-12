package com.archops.plan.dto;

import com.archops.plan.domain.OperationPlanStatus;
import com.archops.plan.domain.PlanBranchKind;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OperationPlanResponse(
        String id,
        String conflictId,
        String diagnosisId,
        String selectedForkId,
        PlanBranchKind branchKind,
        boolean skipsDraft,
        OperationPlanStatus status,
        List<PlanStep> steps,
        String createdBy,
        Instant createdAt,
        String reviewedBy,
        Instant reviewedAt,
        Instant approvedAt,
        /** True only after human approval (APPROVED+); not set in DRAFT_REVIEW. */
        boolean executionIntent
) {
    public record PlanStep(
            int seq,
            String action,
            String description,
            Map<String, String> params
    ) {
    }
}
