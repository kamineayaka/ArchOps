package com.archops.executor;

import com.archops.common.crypto.SecretBox;
import com.archops.curated.mapper.CuratedDraftEventMapper;
import com.archops.curated.mapper.CuratedDraftItemMapper;
import com.archops.curated.mapper.CuratedDraftMapper;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.curated.mapper.HostSshCredentialMapper;
import com.archops.curated.service.HostSshCredentialService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Engine reads host SSH ciphertext from the same PostgreSQL the control plane wrote.
 * Draft/fact mappers stay off this process — 执行引擎 is not a 策展 truth author.
 */
@Configuration
@ConditionalOnProperty(name = "archops.executor.credentials.enabled", havingValue = "true")
@MapperScan(
        basePackageClasses = {HostSshCredentialMapper.class, CuratedObjectMapper.class},
        annotationClass = Mapper.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        CuratedDraftMapper.class,
                        CuratedDraftItemMapper.class,
                        CuratedDraftEventMapper.class,
                        CuratedFactMapper.class
                }
        )
)
@Import({HostSshCredentialService.class, SecretBox.class})
public class ExecutorPersistenceConfig {
}
