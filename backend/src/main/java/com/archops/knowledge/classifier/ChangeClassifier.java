package com.archops.knowledge.classifier;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Rule-based change classifier for tool executions (L0 / L1 / L2).
 * Conservative defaults: read-only-ish → L0; uncertain → L1.
 */
@Service
public class ChangeClassifier {

    private static final Pattern READ_ONLY_CMD = Pattern.compile(
            "\\b(df|free|uptime|top|ps|tail|cat|ls|head|hostname|uname|whoami|id|date|env|pwd|"
                    + "docker\\s+ps|kubectl\\s+get|systemctl\\s+status)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DISCOVERY_KEYWORDS = Pattern.compile(
            "\\b(namenode|datanode|hive|spark|resourcemanager|nodemanager|hdfs|yarn|"
                    + "role\\s*discovery|discovered\\s+role|cluster\\s+role)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern L2_KEYWORDS = Pattern.compile(
            "\\b(deploy|install|restart|scale)\\b|rm\\s+-rf|systemctl\\s+stop|kubectl\\s+apply",
            Pattern.CASE_INSENSITIVE);

    private static final String PROPOSE_TOOL = "propose_architecture_update";

    public Classification classify(String toolName, String argumentsJson, String output) {
        String tool = toolName != null ? toolName.strip() : "";
        String args = argumentsJson != null ? argumentsJson : "";
        String out = output != null ? output : "";
        String combined = (args + "\n" + out).toLowerCase(Locale.ROOT);

        if (PROPOSE_TOOL.equals(tool)) {
            return new Classification(ChangeLevel.L1, "propose_architecture_update implies architecture discovery");
        }

        if (L2_KEYWORDS.matcher(combined).find() || L2_KEYWORDS.matcher(args).find()) {
            return new Classification(ChangeLevel.L2, "mutating / high-impact keywords (deploy/install/restart/rm -rf/kubectl apply/scale)");
        }

        if (DISCOVERY_KEYWORDS.matcher(combined).find()) {
            return new Classification(ChangeLevel.L1, "architecture / role discovery keywords in output or args");
        }

        if ("ssh_exec".equals(tool)) {
            String command = extractCommand(args);
            if (command != null && READ_ONLY_CMD.matcher(command).find() && !L2_KEYWORDS.matcher(command).find()) {
                return new Classification(ChangeLevel.L0, "ssh_exec read-only diagnostic command");
            }
            if (command != null && looksMutating(command)) {
                return new Classification(ChangeLevel.L2, "ssh_exec command appears mutating");
            }
            if (command != null && looksReadOnly(command)) {
                return new Classification(ChangeLevel.L0, "ssh_exec appears read-only");
            }
            return new Classification(ChangeLevel.L1, "ssh_exec uncertain; treating as potential discovery");
        }

        if (looksReadOnly(combined) && !DISCOVERY_KEYWORDS.matcher(combined).find()) {
            return new Classification(ChangeLevel.L0, "read-only-ish tool output; default L0");
        }

        return new Classification(ChangeLevel.L1, "uncertain change; default L1");
    }

    private static String extractCommand(String argumentsJson) {
        // lightweight parse: "command":"..."
        int idx = argumentsJson.indexOf("\"command\"");
        if (idx < 0) {
            return argumentsJson;
        }
        int colon = argumentsJson.indexOf(':', idx);
        if (colon < 0) {
            return argumentsJson;
        }
        int startQuote = argumentsJson.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return argumentsJson;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startQuote + 1; i < argumentsJson.length(); i++) {
            char c = argumentsJson.charAt(i);
            if (c == '\\' && i + 1 < argumentsJson.length()) {
                sb.append(argumentsJson.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean looksMutating(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("rm -rf")
                || t.contains("systemctl stop")
                || t.contains("kubectl apply")
                || t.contains("restart")
                || t.contains("deploy")
                || t.contains("install ")
                || t.contains(" scale");
    }

    private static boolean looksReadOnly(String text) {
        return READ_ONLY_CMD.matcher(text).find();
    }
}
