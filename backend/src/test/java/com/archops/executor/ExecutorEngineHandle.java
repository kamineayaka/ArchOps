package com.archops.executor;

import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.common.ssh.SshCallRecord;
import com.archops.executor.tls.ExecutorMtls;
import com.archops.executor.tls.MtlsPemFiles;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.util.ArrayList;
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
        return start(null);
    }

    public static ExecutorEngineHandle start(DataSource dataSource) {
        MtlsPemFiles mtls = MtlsPemFiles.generate();
        ArrayList<String> args = new ArrayList<>();
        args.add("--spring.main.web-application-type=none");
        args.add("--archops.ssh.mode=fake");
        args.add("--archops.executor.grpc.port=0");
        args.add("--archops.executor.tls.ca-cert=" + mtls.caCert());
        args.add("--archops.executor.tls.server-cert=" + mtls.serverCert());
        args.add("--archops.executor.tls.server-key=" + mtls.serverKey());
        args.add("--spring.flyway.enabled=false");
        args.add("--spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        if (dataSource != null) {
            args.add("--archops.executor.credentials.enabled=true");
        } else {
            args.add("--archops.executor.credentials.enabled=false");
        }
        ConfigurableApplicationContext context = ExecutorApplication.run(dataSource, args.toArray(String[]::new));
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
