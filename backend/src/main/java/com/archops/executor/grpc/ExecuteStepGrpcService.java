package com.archops.executor.grpc;

import com.archops.common.exception.BusinessException;
import com.archops.common.ssh.ControlledSshPort;
import com.archops.common.ssh.PlanStepCommands;
import com.archops.common.ssh.SshExecRequest;
import com.archops.common.ssh.SshExecResult;
import com.archops.curated.service.HostSshCredentialService;
import com.archops.executor.StepAssertionJudge;
import com.archops.executor.v1.ExecuteStepRequest;
import com.archops.executor.v1.ExecuteStepResponse;
import com.archops.executor.v1.ExecutorGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Engine-side ExecuteStep: run one frozen tool call; do not read 操作计划 rows.
 */
@Component
public class ExecuteStepGrpcService extends ExecutorGrpc.ExecutorImplBase {

    private final ControlledSshPort sshPort;
    private final ObjectProvider<HostSshCredentialService> credentials;
    private final ObjectMapper objectMapper;

    public ExecuteStepGrpcService(
            ControlledSshPort sshPort,
            ObjectProvider<HostSshCredentialService> credentials,
            ObjectMapper objectMapper
    ) {
        this.sshPort = sshPort;
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    @Override
    public void executeStep(ExecuteStepRequest request, StreamObserver<ExecuteStepResponse> responseObserver) {
        ExecuteStepResponse response;
        try {
            HostSshCredentialService credentialService = credentials.getIfAvailable();
            if (credentialService == null) {
                throw new BusinessException("HOST_SSH_CREDENTIAL_NOT_FOUND",
                        "No SSH credential configured for host: " + request.getTargetHostId());
            }
            credentialService.requireDecrypted(request.getTargetHostId());
            String command = PlanStepCommands.command(
                    request.getAction(), request.getParamsMap(), request.getTargetHostId());
            SshExecResult result = sshPort.exec(new SshExecRequest(
                    request.getTargetHostId(),
                    command,
                    request.getAction(),
                    request.getStepSeq(),
                    request.getParamsMap()
            ));
            response = toJudgedResponse(request, result);
        } catch (BusinessException ex) {
            response = toResponse(request.getStepSeq(), false, "", ex.getMessage());
        } catch (RuntimeException ex) {
            response = toResponse(request.getStepSeq(), false, "", "SSH execution blocked: " + ex.getMessage());
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private ExecuteStepResponse toJudgedResponse(ExecuteStepRequest request, SshExecResult result) {
        String stdout = result.stdout() == null ? "" : result.stdout();
        if (!result.success()) {
            return toResponse(request.getStepSeq(), false, stdout, result.failureReason());
        }
        String mismatch = StepAssertionJudge.mismatchReason(stdout, request.getExpectedMap(), objectMapper);
        if (mismatch != null) {
            return toResponse(request.getStepSeq(), false, stdout, mismatch);
        }
        return toResponse(request.getStepSeq(), true, stdout, null);
    }

    private static ExecuteStepResponse toResponse(
            int stepSeq,
            boolean success,
            String structuredOutput,
            String failureReason
    ) {
        return ExecuteStepResponse.newBuilder()
                .setStepSeq(stepSeq)
                .setSuccess(success)
                .setStructuredOutput(structuredOutput == null ? "" : structuredOutput)
                .setFailureReason(failureReason == null ? "" : failureReason)
                .build();
    }
}
