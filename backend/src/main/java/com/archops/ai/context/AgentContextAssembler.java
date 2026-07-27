package com.archops.ai.context;

import com.archops.ai.dto.UiContext;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.knowledge.architecture.ArchitectureProperties;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.hybrid.HybridRetrievalResult;
import com.archops.knowledge.hybrid.HybridRetrievalService;
import com.archops.knowledge.repository.WorkLogRepository;
import com.archops.user.domain.Role;
import com.archops.user.domain.User;
import com.archops.user.repository.UserRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OpsKat-style PromptBuilder slots for ArchOps.
 * Content SSOT priority: Graph neighborhood → Architecture facts → scoped text memory → Work Log.
 * Never treat asset Description as SSOT.
 */
@Service
public class AgentContextAssembler {

    public static final String HEADER_IDENTITY = "## Identity & safety rules";
    public static final String HEADER_TARGETS = "## Conversation targets";
    public static final String HEADER_GRAPH = "## Graph neighborhood";
    public static final String HEADER_ARCHITECTURE = "## Active Architecture facts";
    public static final String HEADER_TEXT_MEMORY = "## Scoped text memory";
    public static final String HEADER_WORK_LOGS = "## Recent work logs";
    public static final String HEADER_UI = "## UI surface";
    public static final String HEADER_SECRETS = "## Secrets & overreach warnings";

    private static final int WORK_LOG_LIMIT = 10;
    private static final int WORK_LOG_SUMMARY_CHARS = 200;

    private static final String IDENTITY_RULES = """
            You are ArchOps AI, an expert SRE assistant for a cloud-native operations platform.
            You help operators inspect and manage Linux server clusters, Kubernetes, Docker,
            and big-data stacks (Spark, Kafka, MinIO, Prometheus, Hadoop/HDFS/Hive).

            Knowledge policy (Knowledge-Graph-augmented Hybrid RAG):
            1. Prefer Graph neighborhood and Active Architecture facts before probing hosts or text memory.
            2. Scoped text memory is unstructured long-tail recall (work logs / manuals / prose) — not topology SSOT.
            3. L0 read-only diagnostics (df/free/uptime/ps/ls/…): do NOT write architecture.
            4. When you discover durable facts (roles like namenode/datanode/hive/spark, topology),
               you MUST call propose_architecture_update — never claim SSOT was updated directly.
            5. partitionKey (scope) rules: graph:global | cluster:{elementId} | tag:{slug} | asset:{id}.
               Prefer asset / tag / cluster scopes over graph:global when evidence is local.
               Legacy global | asset:{numericId} still accepted during migration.
            6. Use graph_neighborhood / graph_path tools for deeper topology beyond the seeded neighborhood.

            Always prefer safe read-only diagnostics first. Respond in the same language the user writes in.
            """;

    private static final String SECRETS_WARNINGS = """
            - Never echo credentials, private keys, tokens, or secret env values into chat or proposals.
            - Do not expand tool scope beyond conversation targets and user ACL.
            - UI surface hints are advisory only; server targets and ACL are authoritative.
            - Do not dump entire architecture partitions; use facts, graph neighborhood, and scoped text memory only.
            """;

    private final AssetService assetService;
    private final HybridRetrievalService hybridRetrievalService;
    private final ObjectProvider<ArchitectureProperties> architectureProperties;
    private final WorkLogRepository workLogRepository;
    private final UserRepository userRepository;

    public AgentContextAssembler(
            AssetService assetService,
            HybridRetrievalService hybridRetrievalService,
            ObjectProvider<ArchitectureProperties> architectureProperties,
            WorkLogRepository workLogRepository,
            UserRepository userRepository) {
        this.assetService = assetService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.architectureProperties = architectureProperties;
        this.workLogRepository = workLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public String assemble(
            Long userId,
            List<Long> assetIds,
            String userQuery,
            Long conversationId,
            UiContext uiContext) {
        return assemble(userId, assetIds, userQuery, conversationId, uiContext, null);
    }

    /**
     * Assembles the agent system prompt. When {@code contextCharBudgetOverride} is positive,
     * it caps the assembled prompt (e.g. from Provider {@code contextWindow}); otherwise the
     * platform {@code architecture.context-max-chars} default is used.
     */
    @Transactional(readOnly = true)
    public String assemble(
            Long userId,
            List<Long> assetIds,
            String userQuery,
            Long conversationId,
            UiContext uiContext,
            Integer contextCharBudgetOverride) {
        List<Long> assets = assetIds != null ? assetIds : List.of();
        List<String> roles = loadRoles(userId);

        HybridRetrievalResult hybrid;
        try {
            hybrid = hybridRetrievalService.retrieve(userQuery, assets, userId, roles);
        } catch (Exception ex) {
            hybrid = HybridRetrievalResult.empty();
        }

        StringBuilder sb = new StringBuilder();
        appendSection(sb, HEADER_IDENTITY, IDENTITY_RULES.trim());
        appendSection(sb, HEADER_TARGETS, formatTargets(assets));
        appendSection(sb, HEADER_GRAPH, hybrid.graphNeighborhood());
        appendSection(sb, HEADER_ARCHITECTURE, hybrid.architectureFacts());
        appendSection(sb, HEADER_TEXT_MEMORY, hybrid.textMemory());
        appendSection(sb, HEADER_WORK_LOGS, formatWorkLogs(conversationId, assets));
        String uiHint = formatUiContext(uiContext);
        if (uiHint != null && !uiHint.isBlank()) {
            appendSection(sb, HEADER_UI, uiHint);
        }
        appendSection(sb, HEADER_SECRETS, SECRETS_WARNINGS.trim());

        return truncatePreferringGraph(sb.toString().trim(), contextCharBudgetOverride);
    }

    private List<String> loadRoles(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRepository.findById(userId)
                .map(User::getRoles)
                .map(roles -> roles.stream().map(Role::getName).toList())
                .orElse(List.of());
    }

    /**
     * Truncate from the end so identity/targets/graph/facts are preserved longer than text memory.
     */
    private String truncatePreferringGraph(String result, Integer contextCharBudgetOverride) {
        int maxChars;
        if (contextCharBudgetOverride != null && contextCharBudgetOverride > 0) {
            maxChars = contextCharBudgetOverride;
        } else {
            maxChars = architectureProperties.stream()
                    .findFirst()
                    .map(ArchitectureProperties::getContextMaxChars)
                    .orElse(4000);
        }
        if (maxChars <= 0 || result.length() <= maxChars) {
            return result;
        }
        // Drop from the end so identity / targets / graph / facts survive longer than text memory tails
        return result.substring(0, maxChars) + "…";
    }

    private static void appendSection(StringBuilder sb, String header, String body) {
        if (body == null || body.isBlank()) {
            sb.append(header).append("\n(none)\n\n");
            return;
        }
        sb.append(header).append('\n').append(body.trim()).append("\n\n");
    }

    private String formatTargets(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return "Active target assets: none. Ask the user to select target assets before running ssh_exec, "
                    + "or pass assetId explicitly after listing assets.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Active target assets. When ssh_exec omits assetId, the command runs on ALL targets sequentially:\n");
        for (Long assetId : assetIds) {
            try {
                AssetResponse asset = assetService.get(assetId);
                sb.append("- id=").append(asset.id())
                        .append(" elementId=").append(asset.elementId())
                        .append(" name=").append(asset.name())
                        .append(" host=").append(asset.host() != null ? asset.host() : "n/a")
                        .append(" kind=").append(asset.kind())
                        .append('\n');
            } catch (Exception ex) {
                sb.append("- id=").append(assetId).append(" (unavailable)\n");
            }
        }
        return sb.toString();
    }

    private String formatWorkLogs(Long conversationId, List<Long> assetIds) {
        List<WorkLog> logs;
        if (conversationId != null) {
            logs = workLogRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        } else if (assetIds != null && !assetIds.isEmpty()) {
            logs = workLogRepository.findFiltered(null, assetIds.getFirst(), null);
        } else {
            logs = workLogRepository.findTop20ByOrderByCreatedAtDesc();
        }
        if (logs == null || logs.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (WorkLog log : logs) {
            if (count >= WORK_LOG_LIMIT) {
                break;
            }
            sb.append("- ");
            if (log.getLevel() != null) {
                sb.append('[').append(log.getLevel()).append("] ");
            }
            if (log.getLogType() != null) {
                sb.append(log.getLogType()).append(": ");
            }
            sb.append(truncate(log.getSummary(), WORK_LOG_SUMMARY_CHARS)).append('\n');
            count++;
        }
        return sb.toString();
    }

    private static String formatUiContext(UiContext uiContext) {
        if (uiContext == null) {
            return null;
        }
        boolean empty = (uiContext.route() == null || uiContext.route().isBlank())
                && (uiContext.surface() == null || uiContext.surface().isBlank())
                && uiContext.selectedAssetId() == null
                && (uiContext.selectedAssetIds() == null || uiContext.selectedAssetIds().isEmpty());
        if (empty) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Client UI hint (advisory only):\n");
        if (uiContext.route() != null && !uiContext.route().isBlank()) {
            sb.append("- route=").append(uiContext.route()).append('\n');
        }
        if (uiContext.surface() != null && !uiContext.surface().isBlank()) {
            sb.append("- surface=").append(uiContext.surface()).append('\n');
        }
        if (uiContext.selectedAssetId() != null) {
            sb.append("- selectedAssetId=").append(uiContext.selectedAssetId()).append('\n');
        }
        if (uiContext.selectedAssetIds() != null && !uiContext.selectedAssetIds().isEmpty()) {
            sb.append("- selectedAssetIds=").append(uiContext.selectedAssetIds()).append('\n');
        }
        return sb.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
