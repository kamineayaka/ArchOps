package com.archops.knowledge.architecture.config;

import com.archops.knowledge.architecture.ArchitectureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArchitectureProperties.class)
public class ArchitecturePropertiesConfig {}
