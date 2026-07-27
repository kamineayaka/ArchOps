package com.archops.graph.neo4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Neo4jSchemaInitializerTest {

    @Test
    void loadsConstraintStatementsFromClasspath() {
        List<String> statements = Neo4jSchemaInitializer.loadStatements();
        assertFalse(statements.isEmpty());
        assertTrue(statements.stream().anyMatch(s -> s.contains("asset_element_id")));
        assertTrue(statements.stream().anyMatch(s -> s.contains("CREATE INDEX asset_kind")));
        assertTrue(statements.stream().noneMatch(s -> s.endsWith(";")));
    }
}
