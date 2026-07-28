package com.archops.graph.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness probe for the hand-rolled Neo4j Driver (Boot Neo4j autoconfig is excluded). */
@Component("neo4jHealthIndicator")
public class Neo4jHealthIndicator implements HealthIndicator {

    private final Driver driver;
    private final GraphProperties properties;

    public Neo4jHealthIndicator(Driver driver, GraphProperties properties) {
        this.driver = driver;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            session.run("RETURN 1").consume();
            return Health.up().withDetail("database", properties.getDatabase()).build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
