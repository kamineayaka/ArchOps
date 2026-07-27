package com.archops.asset.dbquery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Pluggable JDBC dialect adapter for DATABASE assets. */
public interface DbEngine {

    String id();

    Connection open(String host, int port, String database, String username, String password)
            throws SQLException;

    DbQueryResult execute(Connection connection, String sql, int maxRows, int timeoutSeconds)
            throws SQLException;

    record DbQueryResult(
            List<String> columns, List<List<Object>> rows, int rowCount, boolean truncated, long updateCount) {}
}
