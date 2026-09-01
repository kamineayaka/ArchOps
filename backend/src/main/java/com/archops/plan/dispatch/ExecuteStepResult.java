package com.archops.plan.dispatch;

import com.archops.common.ssh.SshExecResult;

public record ExecuteStepResult(
        int stepSeq,
        boolean success,
        String structuredOutput,
        String failureReason
) {
    public static ExecuteStepResult from(int stepSeq, SshExecResult result) {
        return new ExecuteStepResult(
                stepSeq,
                result.success(),
                result.stdout(),
                result.failureReason()
        );
    }
}
