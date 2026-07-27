package com.archops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Neo4j driver is optional ({@code archops.graph.enabled}). Exclude Spring Boot's Neo4j
 * autoconfig so a classpath-only {@code neo4j-java-driver} does not create a default
 * {@code bolt://localhost:7687} Driver + Neo4j health indicator when graph is off.
 */
@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class})
@EnableScheduling
public class ArchOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchOpsApplication.class, args);
    }
}
