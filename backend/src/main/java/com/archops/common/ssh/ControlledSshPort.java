package com.archops.common.ssh;

/**
 * Control-plane SSH execution port. Production = MINA SSHD; CI/default = recording fake.
 * Not a second acceptance seam — HTTP remains the primary seam.
 */
public interface ControlledSshPort {

    SshExecResult exec(SshExecRequest request);
}
