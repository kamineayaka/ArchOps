package com.archops.plan.dispatch;

import java.util.Map;

/**
 * One frozen step dispatched by the 控制面 (cursor stays here; engine does not load 操作计划).
 */
public record ExecuteStepCommand(
        String planId,
        int stepSeq,
        String action,
        Map<String, String> params,
        String targetHostId
) {
}
