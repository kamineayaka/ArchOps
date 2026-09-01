package com.archops.executor;

import com.archops.common.ssh.MinaSshPort;
import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.executor.tls.MtlsPemFiles;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Independent 执行引擎 process (ADR-0044). MINA/fake SSH live here; not a truth author.
 */
@SpringBootApplication
@Import({RecordingFakeSshPort.class, MinaSshPort.class})
public class ExecutorApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "--health-probe".equals(args[0])) {
            System.exit(ExecutorHealthProbe.run());
            return;
        }
        if (args.length > 1 && "--generate-mtls".equals(args[0])) {
            MtlsPemFiles.generateTo(Path.of(args[1]));
            return;
        }
        run(args);
    }

    public static ConfigurableApplicationContext run(String... args) {
        return run(null, args);
    }

    public static ConfigurableApplicationContext run(DataSource dataSource, String... args) {
        SpringApplication app = new SpringApplication(ExecutorApplication.class);
        app.setAdditionalProfiles("executor");
        if (dataSource != null) {
            app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("dataSource", dataSource));
        }
        return app.run(args);
    }
}
