package com.archops.graph.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GraphProperties.class)
public class GraphNeo4jConfig {

    @Bean(destroyMethod = "close")
    Driver neo4jDriver(GraphProperties properties) {
        return GraphDatabase.driver(
                properties.getUri(),
                AuthTokens.basic(properties.getUsername(), properties.getPassword()));
    }
}
