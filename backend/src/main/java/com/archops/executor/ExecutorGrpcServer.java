package com.archops.executor;

import com.archops.executor.grpc.ExecuteStepGrpcService;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * gRPC listener for ExecuteStep (and later grpc.health.v1).
 */
@Component
public class ExecutorGrpcServer {

    private final Server server;

    public ExecutorGrpcServer(
            ExecuteStepGrpcService executeStepGrpcService,
            @Value("${archops.executor.grpc.port:8443}") int port
    ) {
        this.server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(executeStepGrpcService)
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
