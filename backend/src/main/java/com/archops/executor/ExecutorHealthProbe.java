package com.archops.executor;

import com.archops.common.net.HostPort;
import com.archops.executor.tls.ExecutorMtls;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import java.nio.file.Path;

/**
 * Compose/CI probe: grpc.health.v1 SERVING under the control-plane client certificate.
 */
public final class ExecutorHealthProbe {

    private ExecutorHealthProbe() {
    }

    public static int run() {
        String address = env("ARCHOPS_EXECUTOR_ADDRESS", "127.0.0.1:" + env("ARCHOPS_EXECUTOR_GRPC_PORT", "8443"));
        Path ca = Path.of(requiredEnv("ARCHOPS_EXECUTOR_TLS_CA_CERT"));
        Path cert = Path.of(requiredEnv("ARCHOPS_EXECUTOR_TLS_CLIENT_CERT"));
        Path key = Path.of(requiredEnv("ARCHOPS_EXECUTOR_TLS_CLIENT_KEY"));
        HostPort hostPort = HostPort.parse(address);
        ManagedChannel channel = NettyChannelBuilder.forAddress(hostPort.host(), hostPort.port())
                .sslContext(ExecutorMtls.clientContext(cert, key, ca))
                .build();
        try {
            HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                    .check(HealthCheckRequest.getDefaultInstance());
            return response.getStatus() == HealthCheckResponse.ServingStatus.SERVING ? 0 : 1;
        } catch (StatusRuntimeException ex) {
            System.err.println("执行引擎 health probe failed: " + ex.getStatus());
            return 1;
        } finally {
            channel.shutdownNow();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name + " for 执行引擎 health probe");
        }
        return value;
    }
}
