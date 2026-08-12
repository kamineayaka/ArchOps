package com.archops.plan.dto;

public record StartExecutionResponse(
        String planId,
        String status,
        String message
) {
}
