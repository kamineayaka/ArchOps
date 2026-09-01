package com.archops.executor;

import com.archops.common.crypto.SecretBox;
import com.archops.curated.service.HostSshCredentialService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Engine reads host SSH ciphertext from the same PostgreSQL the control plane wrote.
 */
@Configuration
@ConditionalOnProperty(name = "archops.executor.credentials.enabled", havingValue = "true")
@MapperScan(basePackages = "com.archops.curated.mapper", annotationClass = Mapper.class)
@Import({HostSshCredentialService.class, SecretBox.class})
public class ExecutorPersistenceConfig {
}
