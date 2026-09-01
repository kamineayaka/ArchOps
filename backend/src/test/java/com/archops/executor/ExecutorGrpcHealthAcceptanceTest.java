package com.archops.executor;

import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void healthCheckWithoutClientCertificateIsRejected() throws Exception {
        try (ExecutorEngineHandle engine = ExecutorEngineHandle.start()) {
            var channel = NettyChannelBuilder.forAddress("127.0.0.1", engine.port())
                    .sslContext(GrpcSslContexts.forClient()
                            .trustManager(engine.mtls().caCert().toFile())
                            .build())
                    .build();
            try {
                assertThatThrownBy(() -> HealthGrpc.newBlockingStub(channel)
                        .check(HealthCheckRequest.getDefaultInstance()))
                        .isInstanceOf(StatusRuntimeException.class);
            } finally {
                channel.shutdownNow();
            }
        }
    }

    @Test
    void healthCheckWithWrongClientCertificateIsRejected() throws Exception {
        try (ExecutorEngineHandle engine = ExecutorEngineHandle.start()) {
            var channel = NettyChannelBuilder.forAddress("127.0.0.1", engine.port())
                    .sslContext(GrpcSslContexts.forClient()
                            .keyManager(engine.mtls().wrongClientCert().toFile(), engine.mtls().wrongClientKey().toFile())
                            .trustManager(engine.mtls().caCert().toFile())
                            .build())
                    .build();
            try {
                assertThatThrownBy(() -> HealthGrpc.newBlockingStub(channel)
                        .check(HealthCheckRequest.getDefaultInstance()))
                        .isInstanceOf(StatusRuntimeException.class);
            } finally {
                channel.shutdownNow();
            }
        }
    }
}
