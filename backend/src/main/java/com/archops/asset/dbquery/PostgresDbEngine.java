package com.archops.asset.dbquery;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PostgresDbEngine implements DbEngine {

    @Override
    public String id() {
        return "postgresql";
    }

    @Override
    public Connection open(String host, int port, String database, String username, String password)
            throws SQLException {
        String db = StringUtils.hasText(database) ? database.trim() : "postgres";
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password != null ? password : "");
        props.setProperty("connectTimeout", "10");
        props.setProperty("socketTimeout", "30");
        props.setProperty("loginTimeout", "10");
        props.setProperty("ApplicationName", "archops-db-query");
        return DriverManager.getConnection(url, props);
    }

    @Override
    public DbQueryResult execute(Connection connection, String sql, int maxRows, int timeoutSeconds)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.max(1, timeoutSeconds));
            statement.setMaxRows(Math.max(1, maxRows + 1)); // +1 to detect truncation
            boolean hasResultSet = statement.execute(sql);
            if (hasResultSet) {
                try (ResultSet rs = statement.getResultSet()) {
                    return readResultSet(rs, maxRows);
                }
            }
            long updated = statement.getLargeUpdateCount();
            return new DbQueryResult(List.of(), List.of(), 0, false, Math.max(0L, updated));
        }
    }

    private static DbQueryResult readResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            String label = meta.getColumnLabel(i);
            columns.add(label != null && !label.isBlank() ? label : meta.getColumnName(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                Object value = rs.getObject(i);
                if (value instanceof byte[] bytes) {
                    row.add("\\x" + bytes.length + " bytes");
                } else {
                    row.add(value);
                }
            }
            rows.add(row);
        }
        return new DbQueryResult(columns, rows, rows.size(), truncated, 0L);
    }
}
