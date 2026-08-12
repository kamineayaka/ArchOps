package com.archops.plan.dto;

import java.util.List;

public record StartExecutionResponse(
        String planId,
        String status,
        String message,
        Integer completedSteps,
        String voidReason,
        List<OperationPlanResponse.ExecutionStepLog> executionLog
) {
}
