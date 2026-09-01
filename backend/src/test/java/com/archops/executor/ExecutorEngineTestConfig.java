package com.archops.executor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class ExecutorEngineTestConfig {

    @Bean(destroyMethod = "close")
    ExecutorEngineHandle executorEngineHandle() {
        return ExecutorEngineHandle.start();
    }

    @Bean
    DynamicPropertyRegistrar executorDispatchAddress(ExecutorEngineHandle engine) {
        return registry -> {
            registry.add("archops.executor.address", () -> "127.0.0.1:" + engine.port());
            registry.add("archops.executor.tls.ca-cert", () -> engine.mtls().caCert().toString());
            registry.add("archops.executor.tls.client-cert", () -> engine.mtls().clientCert().toString());
            registry.add("archops.executor.tls.client-key", () -> engine.mtls().clientKey().toString());
        };
    }
}
