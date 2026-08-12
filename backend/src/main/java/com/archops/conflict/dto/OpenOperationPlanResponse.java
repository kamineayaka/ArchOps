package com.archops.conflict.dto;

public record OpenOperationPlanResponse(
        String conflictId,
        String status,
        String handlerUserId,
        String message
) {
}
