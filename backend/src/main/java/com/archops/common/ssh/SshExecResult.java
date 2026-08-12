package com.archops.common.ssh;

public record SshExecResult(
        boolean success,
        int exitCode,
        String stdout,
        String stderr,
        String failureReason
) {
    public static SshExecResult ok(String stdout) {
        return new SshExecResult(true, 0, stdout == null ? "" : stdout, "", null);
    }

    public static SshExecResult fail(String reason) {
        return new SshExecResult(false, 1, "", reason == null ? "" : reason, reason);
    }
}
