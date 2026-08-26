package com.archops.common.ssh;

/**
 * Transitional in-process SSH execution port (ADR-0044: production path moves to the 执行引擎).
 * Production adapter = MINA SSHD; CI/default = recording fake. HTTP remains the primary seam.
 */
public interface ControlledSshPort {

    SshExecResult exec(SshExecRequest request);
}
