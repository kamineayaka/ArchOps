package com.archops.executor;

import com.archops.executor.tls.MtlsPemFiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * mTLS client material without starting the 执行引擎 — for down/unavailable HTTP cases.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ExecutorMtlsClientTestConfig {

    @Bean
    MtlsPemFiles executorClientMtls() {
        return MtlsPemFiles.generate();
    }

    @Bean
    DynamicPropertyRegistrar executorClientTlsProperties(MtlsPemFiles mtls) {
        return registry -> {
            registry.add("archops.executor.tls.ca-cert", () -> mtls.caCert().toString());
            registry.add("archops.executor.tls.client-cert", () -> mtls.clientCert().toString());
            registry.add("archops.executor.tls.client-key", () -> mtls.clientKey().toString());
        };
    }
}
