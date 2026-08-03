package com.archops.graph.semantics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects free-text that encodes topology (jumps / membership / depends / runs-on)
 * which must live as typed Neo4j edges, not description / body_md / facts.
 */
public final class TopologyProseDetector {

    public enum Level {
        NONE,
        WARN,
        HARD
    }

    public record Hit(Level level, String pattern, String excerpt) {}

    public record Result(Level level, List<Hit> hits) {
        public boolean blocksAutoMerge() {
            return level == Level.WARN || level == Level.HARD;
        }

        public boolean isHard() {
            return level == Level.HARD;
        }

        public static Result none() {
            return new Result(Level.NONE, List.of());
        }
    }

    private static final List<Rule> RULES = List.of(
            // Explicit edge-type / jump encoding → HARD
            rule(Level.HARD, "(?i)\\bCONNECTS_VIA\\b"),
            rule(Level.HARD, "(?i)\\bDEPENDS_ON\\b"),
            rule(Level.HARD, "(?i)\\bRUNS_ON\\b"),
            rule(Level.HARD, "(?i)\\bMEMBER_OF\\b"),
            rule(Level.HARD, "(?i)\\bHAS_TAG\\b"),
            rule(Level.HARD, "(?i)\\bjump[_\\s-]?host\\b"),
            rule(Level.HARD, "(?i)\\bjump[_\\s-]?asset\\b"),
            rule(Level.HARD, "(?i)\\bbastion\\b.{0,40}\\b(ssh|jump|via)\\b"),
            rule(Level.HARD, "跳板\\s*(机|主机|服务器|资产|链)?"),
            rule(Level.HARD, "(?i)via\\s+(jump|bastion|gateway)\\b"),
            // Softer dependency / membership language → WARN
            rule(Level.WARN, "(?i)\\bdepends\\s+on\\b"),
            rule(Level.WARN, "(?i)\\bruns\\s+on\\b"),
            rule(Level.WARN, "(?i)\\bmember\\s+of\\b"),
            rule(Level.WARN, "(?i)\\bssh\\s+jump\\b"),
            rule(Level.WARN, "依赖(于|关系|边)?"),
            rule(Level.WARN, "运行在"),
            rule(Level.WARN, "属于(集群|分组|标签)"));

    private TopologyProseDetector() {}

    public static Result scan(String text) {
        if (text == null || text.isBlank()) {
            return Result.none();
        }
        String normalized = text.trim();
        List<Hit> hits = new ArrayList<>();
        Level max = Level.NONE;
        for (Rule rule : RULES) {
            var matcher = rule.pattern.matcher(normalized);
            if (matcher.find()) {
                String excerpt = excerptAround(normalized, matcher.start(), matcher.end());
                hits.add(new Hit(rule.level, rule.pattern.pattern(), excerpt));
                if (rank(rule.level) > rank(max)) {
                    max = rule.level;
                }
            }
        }
        return new Result(max, List.copyOf(hits));
    }

    /** Predicates that must never be stored as architecture facts — use graph edges. */
    public static boolean isTopologyEdgePredicate(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return false;
        }
        String p = predicate.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (p) {
            case "connects_via",
                    "depends_on",
                    "runs_on",
                    "member_of",
                    "has_tag",
                    "jump",
                    "jump_via",
                    "bastion",
                    "依赖",
                    "跳板",
                    "运行在",
                    "属于" -> true;
            default -> false;
        };
    }

    private static Rule rule(Level level, String regex) {
        return new Rule(level, Pattern.compile(regex));
    }

    private static int rank(Level level) {
        return switch (level) {
            case HARD -> 2;
            case WARN -> 1;
            default -> 0;
        };
    }

    private static String excerptAround(String text, int start, int end) {
        int from = Math.max(0, start - 24);
        int to = Math.min(text.length(), end + 24);
        String slice = text.substring(from, to).replaceAll("\\s+", " ").trim();
        if (from > 0) {
            slice = "…" + slice;
        }
        if (to < text.length()) {
            slice = slice + "…";
        }
        return slice;
    }

    private record Rule(Level level, Pattern pattern) {}
}
