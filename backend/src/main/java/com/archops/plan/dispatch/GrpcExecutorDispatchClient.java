package com.archops.plan.dispatch;

import com.archops.common.exception.BusinessException;
import com.archops.executor.v1.ExecuteStepRequest;
import com.archops.executor.v1.ExecuteStepResponse;
import com.archops.executor.v1.ExecutorGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Production 控制面代发: one ExecuteStep RPC per frozen step (ADR-0045).
 */
@Component
@ConditionalOnProperty(name = "archops.ssh.mode", havingValue = "dispatch")
public class GrpcExecutorDispatchClient implements ExecutorDispatchPort {

    private final ManagedChannel channel;
    private final ExecutorGrpc.ExecutorBlockingStub stub;

    public GrpcExecutorDispatchClient(@Value("${archops.executor.address}") String address) {
        this.channel = Grpc.newChannelBuilder(address, InsecureChannelCredentials.create()).build();
        this.stub = ExecutorGrpc.newBlockingStub(channel);
    }

    @Override
    public ExecuteStepResult executeStep(ExecuteStepCommand command) {
        try {
            ExecuteStepResponse response = stub.executeStep(toProto(command));
            return new ExecuteStepResult(
                    response.getStepSeq(),
                    response.getSuccess(),
                    response.getStructuredOutput(),
                    response.getFailureReason().isBlank() ? null : response.getFailureReason()
            );
        } catch (StatusRuntimeException ex) {
            throw new BusinessException("EXECUTOR_UNAVAILABLE",
                    "执行引擎 did not execute the frozen step: " + ex.getStatus());
        }
    }

    private static ExecuteStepRequest toProto(ExecuteStepCommand command) {
        ExecuteStepRequest.Builder request = ExecuteStepRequest.newBuilder()
                .setPlanId(command.planId() == null ? "" : command.planId())
                .setStepSeq(command.stepSeq())
                .setAction(command.action() == null ? "" : command.action())
                .setTargetHostId(command.targetHostId() == null ? "" : command.targetHostId());
        if (command.params() != null) {
            request.putAllParams(command.params());
        }
        return request.build();
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
