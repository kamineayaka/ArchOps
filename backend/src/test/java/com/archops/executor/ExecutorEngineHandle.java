package com.archops.executor;

import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.common.ssh.SshCallRecord;
import com.archops.executor.tls.ExecutorMtls;
import com.archops.executor.tls.MtlsPemFiles;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

/**
 * Same-JVM 执行引擎 fixture: separate Spring context + mTLS gRPC server (ADR-0045).
 */
public final class ExecutorEngineHandle implements AutoCloseable {

    private final ConfigurableApplicationContext context;
    private final MtlsPemFiles mtls;

    public ExecutorEngineHandle(ConfigurableApplicationContext context, MtlsPemFiles mtls) {
        this.context = context;
        this.mtls = mtls;
    }

    public static ExecutorEngineHandle start() {
        MtlsPemFiles mtls = MtlsPemFiles.generate();
        ConfigurableApplicationContext context = ExecutorApplication.run(
                "--spring.main.web-application-type=none",
                "--spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "--archops.ssh.mode=fake",
                "--archops.executor.grpc.port=0",
                "--archops.executor.tls.ca-cert=" + mtls.caCert(),
                "--archops.executor.tls.server-cert=" + mtls.serverCert(),
                "--archops.executor.tls.server-key=" + mtls.serverKey()
        );
        return new ExecutorEngineHandle(context, mtls);
    }

    public int port() {
        return context.getBean(ExecutorGrpcServer.class).port();
    }

    public MtlsPemFiles mtls() {
        return mtls;
    }

    public ManagedChannel controlPlaneChannel() {
        return NettyChannelBuilder.forAddress("127.0.0.1", port())
                .sslContext(ExecutorMtls.clientContext(mtls.clientCert(), mtls.clientKey(), mtls.caCert()))
                .build();
    }

    public RecordingFakeSshPort fakeSsh() {
        return context.getBean(RecordingFakeSshPort.class);
    }

    public List<SshCallRecord> recordedCalls() {
        return fakeSsh().recordedCalls();
    }

    @Override
    public void close() {
        context.close();
    }
}
