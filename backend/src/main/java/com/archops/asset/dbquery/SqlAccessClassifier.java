package com.archops.asset.dbquery;

import com.archops.common.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Conservative SQL read/write classifier for DATABASE query gate.
 * Ambiguous or multi-statement SQL is treated as WRITE (requires approval) or rejected.
 */
@Component
public class SqlAccessClassifier {

    private static final Pattern STRIP_LINE_COMMENT = Pattern.compile("(?m)--.*?$");
    private static final Pattern STRIP_BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LEADING_WITH = Pattern.compile("(?is)^with\\b");

    private static final Set<String> READ_HEADS = Set.of(
            "select", "show", "values", "table", "with");

    private static final Set<String> WRITE_HEADS = Set.of(
            "insert",
            "update",
            "delete",
            "merge",
            "truncate",
            "alter",
            "drop",
            "create",
            "grant",
            "revoke",
            "call",
            "do",
            "copy",
            "vacuum",
            "analyze",
            "refresh",
            "reindex",
            "cluster",
            "comment",
            "security",
            "set",
            "reset",
            "discard",
            "lock",
            "unlock");

    public SqlAccessKind classify(String sql) {
        String normalized = normalize(sql);
        if (normalized.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SQL_REQUIRED", "SQL 不能为空");
        }
        if (normalized.contains(";")) {
            // Allow a single trailing semicolon only
            String withoutTrailing = normalized.replaceAll(";+\\s*$", "");
            if (withoutTrailing.contains(";")) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "SQL_MULTI_STATEMENT", "禁止一次提交多条 SQL 语句");
            }
            normalized = withoutTrailing.trim();
        }
        String head = firstKeyword(normalized);
        if (head == null) {
            return SqlAccessKind.WRITE;
        }
        if ("explain".equals(head)) {
            // EXPLAIN ANALYZE / EXPLAIN (ANALYZE …) executes the underlying statement.
            return looksLikeExplainAnalyze(normalized) ? SqlAccessKind.WRITE : SqlAccessKind.READ;
        }
        if ("with".equals(head)) {
            return classifyWithQuery(normalized);
        }
        if (READ_HEADS.contains(head)) {
            return SqlAccessKind.READ;
        }
        if (WRITE_HEADS.contains(head)) {
            return SqlAccessKind.WRITE;
        }
        // Unknown → require approval
        return SqlAccessKind.WRITE;
    }

    private static boolean looksLikeExplainAnalyze(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        // EXPLAIN ANALYZE …  OR  EXPLAIN (ANALYZE …) / EXPLAIN (ANALYZE true, …)
        return Pattern.compile("(?is)^explain\\s+analyze\\b").matcher(lower).find()
                || Pattern.compile("(?is)^explain\\s*\\([^)]*\\banalyze\\b").matcher(lower).find();
    }

    private static SqlAccessKind classifyWithQuery(String sql) {
        // WITH ... SELECT → read; WITH ... INSERT/UPDATE/... → write
        String lower = sql.toLowerCase(Locale.ROOT);
        // crude: find last CTE body separator then next keyword
        int select = indexOfKeyword(lower, "select");
        int insert = indexOfKeyword(lower, "insert");
        int update = indexOfKeyword(lower, "update");
        int delete = indexOfKeyword(lower, "delete");
        int earliestWrite = minPositive(insert, update, delete);
        if (select < 0) {
            return SqlAccessKind.WRITE;
        }
        if (earliestWrite >= 0 && earliestWrite < select) {
            return SqlAccessKind.WRITE;
        }
        // SELECT appears; if any write keyword exists after, treat as write
        if (earliestWrite > select) {
            return SqlAccessKind.WRITE;
        }
        return SqlAccessKind.READ;
    }

    private static int indexOfKeyword(String lower, String kw) {
        Pattern p = Pattern.compile("(?<!\\w)" + Pattern.quote(kw) + "(?!\\w)");
        var m = p.matcher(lower);
        return m.find() ? m.start() : -1;
    }

    private static int minPositive(int... values) {
        int min = -1;
        for (int v : values) {
            if (v >= 0 && (min < 0 || v < min)) {
                min = v;
            }
        }
        return min;
    }

    static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String s = STRIP_BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        s = STRIP_LINE_COMMENT.matcher(s).replaceAll(" ");
        return s.replace('\u0000', ' ').trim();
    }

    private static String firstKeyword(String sql) {
        if (!LEADING_WITH.matcher(sql).find() && sql.isEmpty()) {
            return null;
        }
        String[] parts = sql.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        return parts[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
