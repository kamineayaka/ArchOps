package com.archops.plan.dispatch;

import com.archops.common.ssh.PlanStepCommands;
import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.common.ssh.SshExecRequest;
import com.archops.common.ssh.SshExecResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Legacy 竖切 path: in-process fake SSH, no 执行引擎 (ADR-0044 决议 7).
 */
@Component
@ConditionalOnProperty(name = "archops.ssh.mode", havingValue = "fake", matchIfMissing = true)
public class LocalFakeExecutorDispatchPort implements ExecutorDispatchPort {

    private final RecordingFakeSshPort fakeSsh;

    public LocalFakeExecutorDispatchPort(RecordingFakeSshPort fakeSsh) {
        this.fakeSsh = fakeSsh;
    }

    @Override
    public ExecuteStepResult executeStep(ExecuteStepCommand command) {
        String commandLine = PlanStepCommands.command(command.action(), command.params(), command.targetHostId());
        SshExecResult result = fakeSsh.exec(new SshExecRequest(
                command.targetHostId(),
                commandLine,
                command.action(),
                command.stepSeq(),
                command.params()
        ));
        return ExecuteStepResult.from(command.stepSeq(), result);
    }
}
