package com.archops.asset.dbquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAccessClassifierTest {

    private SqlAccessClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new SqlAccessClassifier();
    }

    @Test
    void treatsSelectAsRead() {
        assertEquals(SqlAccessKind.READ, classifier.classify("SELECT id FROM assets"));
        assertEquals(SqlAccessKind.READ, classifier.classify("  -- comment\nSELECT 1;"));
        assertEquals(SqlAccessKind.READ, classifier.classify("WITH cte AS (SELECT 1) SELECT * FROM cte"));
        assertEquals(SqlAccessKind.READ, classifier.classify("EXPLAIN SELECT 1"));
    }

    @Test
    void treatsExplainAnalyzeAsWrite() {
        assertEquals(SqlAccessKind.WRITE, classifier.classify("EXPLAIN ANALYZE DELETE FROM assets"));
        assertEquals(SqlAccessKind.WRITE, classifier.classify("EXPLAIN (ANALYZE, BUFFERS) UPDATE assets SET name='x'"));
    }

    @Test
    void treatsMutationsAsWrite() {
        assertEquals(SqlAccessKind.WRITE, classifier.classify("DELETE FROM assets WHERE id = 1"));
        assertEquals(SqlAccessKind.WRITE, classifier.classify("UPDATE assets SET name = 'x'"));
        assertEquals(SqlAccessKind.WRITE, classifier.classify("DROP TABLE assets"));
    }

    @Test
    void rejectsMultiStatement() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> classifier.classify("SELECT 1; SELECT 2"));
        assertEquals("SQL_MULTI_STATEMENT", ex.getCode());
    }

    @Test
    void rejectsBlankSql() {
        BusinessException ex = assertThrows(BusinessException.class, () -> classifier.classify("   "));
        assertEquals("SQL_REQUIRED", ex.getCode());
    }
}
