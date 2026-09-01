package com.archops.executor;

import com.archops.executor.grpc.ExecuteStepGrpcService;
import com.archops.executor.tls.ExecutorMtls;
import io.grpc.Server;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * gRPC listener for ExecuteStep + grpc.health.v1 under mTLS (ADR-0045).
 */
@Component
public class ExecutorGrpcServer {

    private final Server server;

    public ExecutorGrpcServer(
            ExecuteStepGrpcService executeStepGrpcService,
            @Value("${archops.executor.grpc.port:8443}") int port,
            @Value("${archops.executor.tls.ca-cert}") String caCert,
            @Value("${archops.executor.tls.server-cert}") String serverCert,
            @Value("${archops.executor.tls.server-key}") String serverKey
    ) {
        HealthStatusManager health = new HealthStatusManager();
        health.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
        health.setStatus("archops.executor.v1.Executor", HealthCheckResponse.ServingStatus.SERVING);
        this.server = NettyServerBuilder.forPort(port)
                .sslContext(ExecutorMtls.serverContext(Path.of(serverCert), Path.of(serverKey), Path.of(caCert)))
                .addService(executeStepGrpcService)
                .addService(health.getHealthService())
                .build();
        try {
            this.server.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start 执行引擎 gRPC server on port " + port, ex);
        }
    }

    public int port() {
        return server.getPort();
    }

    @PreDestroy
    public void stop() {
        server.shutdown();
    }
}
