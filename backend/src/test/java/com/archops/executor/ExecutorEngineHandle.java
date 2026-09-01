package com.archops.executor;

import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.common.ssh.SshCallRecord;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

/**
 * Same-JVM 执行引擎 fixture: separate Spring context + gRPC server (ADR-0045).
 */
public final class ExecutorEngineHandle implements AutoCloseable {

    private final ConfigurableApplicationContext context;

    public ExecutorEngineHandle(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static ExecutorEngineHandle start() {
        ConfigurableApplicationContext context = ExecutorApplication.run(
                "--spring.main.web-application-type=none",
                "--spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "--archops.ssh.mode=fake",
                "--archops.executor.grpc.port=0"
        );
        return new ExecutorEngineHandle(context);
    }

    public int port() {
        return context.getBean(ExecutorGrpcServer.class).port();
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
