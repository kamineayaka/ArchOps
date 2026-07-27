package com.archops.graph.neo4j;

import com.archops.graph.config.GraphProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Applies classpath:neo4j/init-schema.cypher when graph is enabled.
 * Runs asynchronously with retries so a slow/unavailable Neo4j never blocks Spring Boot startup.
 */
@Component
@Order(40)
@ConditionalOnProperty(prefix = "archops.graph", name = "enabled", havingValue = "true")
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);
    private static final String RESOURCE = "neo4j/init-schema.cypher";
    private static final int MAX_ATTEMPTS = 12;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);

    private final ObjectProvider<Driver> neo4jDriver;
    private final GraphProperties properties;

    public Neo4jSchemaInitializer(ObjectProvider<Driver> neo4jDriver, GraphProperties properties) {
        this.neo4jDriver = neo4jDriver;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("neo4j-schema-init").start(this::initializeWithRetry);
    }

    private void initializeWithRetry() {
        Driver driver = neo4jDriver.getIfAvailable();
        if (driver == null) {
            log.warn("Neo4j schema init skipped: driver bean missing");
            return;
        }
        List<String> statements;
        try {
            statements = loadStatements();
        } catch (Exception ex) {
            log.error("Neo4j schema init aborted: cannot load {}", RESOURCE, ex);
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                applySchema(driver, statements);
                log.info("Neo4j schema initialized ({} statements, attempt {})", statements.size(), attempt);
                return;
            } catch (Exception ex) {
                log.warn(
                        "Neo4j schema init attempt {}/{} failed: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        ex.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error(
                            "Neo4j schema init gave up after {} attempts; graph APIs may fail until Neo4j is healthy",
                            MAX_ATTEMPTS,
                            ex);
                    return;
                }
                try {
                    Thread.sleep(BASE_BACKOFF.multipliedBy(attempt).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Neo4j schema init interrupted");
                    return;
                }
            }
        }
    }

    private void applySchema(Driver driver, List<String> statements) {
        try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            for (String cypher : statements) {
                session.run(cypher).consume();
            }
        }
    }

    static List<String> loadStatements() {
        try {
            ClassPathResource resource = new ClassPathResource(RESOURCE);
            List<String> statements = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                        continue;
                    }
                    current.append(trimmed);
                    if (trimmed.endsWith(";")) {
                        String stmt = current.toString();
                        stmt = stmt.substring(0, stmt.length() - 1).trim();
                        if (!stmt.isEmpty()) {
                            statements.add(stmt);
                        }
                        current.setLength(0);
                    } else {
                        current.append(' ');
                    }
                }
            }
            if (!current.isEmpty()) {
                statements.add(current.toString().trim());
            }
            return statements;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load " + RESOURCE, ex);
        }
    }
}
