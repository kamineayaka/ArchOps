package com.archops.plan.dispatch;

import com.archops.common.exception.BusinessException;
import com.archops.common.net.HostPort;
import com.archops.executor.tls.ExecutorMtls;
import com.archops.executor.v1.ExecuteStepRequest;
import com.archops.executor.v1.ExecuteStepResponse;
import com.archops.executor.v1.ExecutorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Production 控制面代发: one ExecuteStep RPC per frozen step over mTLS (ADR-0045).
 */
@Component
@ConditionalOnProperty(name = "archops.ssh.mode", havingValue = "dispatch")
public class GrpcExecutorDispatchClient implements ExecutorDispatchPort {

    private final ManagedChannel channel;
    private final ExecutorGrpc.ExecutorBlockingStub stub;

    public GrpcExecutorDispatchClient(
            @Value("${archops.executor.address}") String address,
            @Value("${archops.executor.tls.ca-cert}") String caCert,
            @Value("${archops.executor.tls.client-cert}") String clientCert,
            @Value("${archops.executor.tls.client-key}") String clientKey
    ) {
        HostPort hostPort = HostPort.parse(address);
        this.channel = NettyChannelBuilder.forAddress(hostPort.host(), hostPort.port())
                .sslContext(ExecutorMtls.clientContext(Path.of(clientCert), Path.of(clientKey), Path.of(caCert)))
                .build();
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
