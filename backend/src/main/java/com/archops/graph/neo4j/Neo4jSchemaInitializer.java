package com.archops.graph.neo4j;

import com.archops.graph.config.GraphProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

/** Applies classpath:neo4j/init-schema.cypher once at startup when graph is enabled. */
@Component
@Order(40)
@ConditionalOnProperty(prefix = "archops.graph", name = "enabled", havingValue = "true")
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);
    private static final String RESOURCE = "neo4j/init-schema.cypher";

    private final ObjectProvider<Driver> neo4jDriver;
    private final GraphProperties properties;

    public Neo4jSchemaInitializer(ObjectProvider<Driver> neo4jDriver, GraphProperties properties) {
        this.neo4jDriver = neo4jDriver;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Driver driver = neo4jDriver.getIfAvailable();
        if (driver == null) {
            log.warn("Neo4j schema init skipped: driver bean missing");
            return;
        }
        List<String> statements = loadStatements();
        try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            for (String cypher : statements) {
                session.run(cypher).consume();
            }
            log.info("Neo4j schema initialized ({} statements)", statements.size());
        } catch (Exception ex) {
            log.error("Neo4j schema initialization failed", ex);
            throw new IllegalStateException("Neo4j schema init failed: " + ex.getMessage(), ex);
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
