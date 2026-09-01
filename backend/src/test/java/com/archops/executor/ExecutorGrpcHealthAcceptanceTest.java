package com.archops.executor;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Process seam: 执行引擎 grpc.health.v1 SERVING when probed with the control-plane client cert.
 */
class ExecutorGrpcHealthAcceptanceTest {

    @Test
    void healthCheckWithControlPlaneClientCertIsServing() throws Exception {
        try (ExecutorEngineHandle engine = ExecutorEngineHandle.start()) {
            var channel = engine.controlPlaneChannel();
            try {
                HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                        .check(HealthCheckRequest.getDefaultInstance());
                assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
            } finally {
                channel.shutdownNow();
            }
        }
    }
}
