package com.archops.executor;

import com.archops.common.ssh.RecordingFakeSshPort;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * Independent 执行引擎 process (ADR-0044). MINA/fake SSH live here; not a truth author.
 */
@SpringBootApplication
@Import(RecordingFakeSshPort.class)
public class ExecutorApplication {

    public static void main(String[] args) {
        run(args);
    }

    public static ConfigurableApplicationContext run(String... args) {
        SpringApplication app = new SpringApplication(ExecutorApplication.class);
        app.setAdditionalProfiles("executor");
        return app.run(args);
    }
}
