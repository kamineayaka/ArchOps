package com.archops.approval.service;

import com.archops.approval.domain.RiskLevel;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Classifies operational risk from tool name and argument payload.
 * Rules are intentionally conservative for production deployments.
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

    /** Explicit read-only asset probes — never escalate via argument pattern matches. */
    private static final Set<String> READONLY_PROBE_TOOLS =
            Set.of("list_assets", "db_ping", "k8s_get");

    public RiskLevel classify(String toolName, String arguments) {
        if (toolName != null && READONLY_PROBE_TOOLS.contains(toolName)) {
            return RiskLevel.LOW;
        }
        String text = ((toolName != null ? toolName : "") + " " + (arguments != null ? arguments : ""))
                .toLowerCase();
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
}
