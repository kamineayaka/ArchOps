package com.archops.approval.service;

import com.archops.approval.domain.RiskLevel;
import com.archops.asset.dbquery.SqlAccessClassifier;
import com.archops.asset.dbquery.SqlAccessKind;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Classifies operational risk from tool name and argument payload.
 * Rules are intentionally conservative for production deployments.
 * Mutating {@code db_query} is always HIGH so writes require approval under all policies including AUTO_C.
 */
@Component
public class RiskClassifier {

    private static final List<Pattern> HIGH = List.of(
            Pattern.compile("\\brm\\s+-rf\\b"),
            Pattern.compile("\\brm\\s+-r\\b"),
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile("\\bdd\\s+if="),
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
            Pattern.compile("\\bkubectl\\s+delete\\b"),
            Pattern.compile("\\bdocker\\s+rm\\b"),
            Pattern.compile("\\btruncate\\b"),
            Pattern.compile("\\bformat\\b"));

    private static final List<Pattern> MEDIUM = List.of(
            Pattern.compile("\\bkubectl\\s+scale\\b"),
            Pattern.compile("\\bkubectl\\s+rollout\\b"),
            Pattern.compile("\\bdocker\\s+(restart|stop|start|rmi)\\b"),
            Pattern.compile("\\bchmod\\b"),
            Pattern.compile("\\bchown\\b"),
            Pattern.compile("\\bkill\\b"),
            Pattern.compile("\\bsystemctl\\s+(restart|stop|disable)\\b"),
            Pattern.compile("\\bapt(-get)?\\s+(install|remove|purge)\\b"),
            Pattern.compile("\\byum\\s+(install|remove)\\b"),
            Pattern.compile("\\bwget\\b"),
            Pattern.compile("\\bcurl\\b.*\\|\\s*sh"));

    private final SqlAccessClassifier sqlAccessClassifier;
    private final ObjectMapper objectMapper;

    public RiskClassifier(SqlAccessClassifier sqlAccessClassifier, ObjectMapper objectMapper) {
        this.sqlAccessClassifier = sqlAccessClassifier;
        this.objectMapper = objectMapper;
    }

    public RiskLevel classify(String toolName, String arguments) {
        if ("db_query".equals(toolName)) {
            return classifyDbQuery(arguments);
        }
        if ("propose_graph_change".equals(toolName)) {
            return classifyGraphChange(arguments);
        }
        String text = ((toolName != null ? toolName : "") + " " + (arguments != null ? arguments : ""))
                .toLowerCase(Locale.ROOT);
        for (Pattern pattern : HIGH) {
            if (pattern.matcher(text).find()) {
                return RiskLevel.HIGH;
            }
        }
        for (Pattern pattern : MEDIUM) {
            if (pattern.matcher(text).find()) {
                return RiskLevel.MEDIUM;
            }
        }
        return RiskLevel.LOW;
    }

    private RiskLevel classifyGraphChange(String arguments) {
        String text = arguments != null ? arguments.toUpperCase(Locale.ROOT) : "";
        if (text.contains("NODE_SOFT_DELETE")
                || text.contains("NODE_DELETE")
                || text.contains("EDGE_SOFT_DELETE")
                || text.contains("NODE_CREATE")
                || text.contains("CREDENTIAL")) {
            return RiskLevel.HIGH;
        }
        if (text.contains("EDGE_CREATE")
                || text.contains("NODE_UPDATE")
                || text.contains("EDGE_UPDATE")) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.MEDIUM;
    }

    private RiskLevel classifyDbQuery(String arguments) {
        String sql = extractSql(arguments);
        if (sql == null || sql.isBlank()) {
            return RiskLevel.HIGH;
        }
        try {
            SqlAccessKind kind = sqlAccessClassifier.classify(sql);
            return kind == SqlAccessKind.READ ? RiskLevel.LOW : RiskLevel.HIGH;
        } catch (Exception ex) {
            return RiskLevel.HIGH;
        }
    }

    private String extractSql(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> args = objectMapper.readValue(arguments, new TypeReference<>() {});
            Object sql = args.get("sql");
            return sql != null ? String.valueOf(sql) : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
