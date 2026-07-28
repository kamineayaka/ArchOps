package com.archops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Neo4j is a required dependency (graph inventory SSOT). Boot Neo4j autoconfig is excluded
 * so we use a single hand-rolled {@code Driver} bean + {@link com.archops.graph.config.Neo4jHealthIndicator}.
 */
@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class})
@EnableScheduling
public class ArchOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchOpsApplication.class, args);
    }
}
