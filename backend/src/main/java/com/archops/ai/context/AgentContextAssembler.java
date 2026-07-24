package com.archops.ai.context;

import com.archops.ai.dto.UiContext;
import com.archops.ai.provider.service.PlatformAiSettingsService;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.knowledge.architecture.ArchitectureProperties;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.dto.ArchitectureViewResponse;
import com.archops.knowledge.architecture.service.ArchitectureViewService;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.repository.WorkLogRepository;
import com.archops.knowledge.retrieval.RagRetrievalService;
import com.archops.knowledge.retrieval.RagScope;
import com.archops.knowledge.retrieval.ScoredChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OpsKat-style PromptBuilder slots for ArchOps.
 * Content SSOT: Architecture facts + scoped RAG + Work Log — never asset Description.
 */
@Service
public class AgentContextAssembler {

    public static final String HEADER_IDENTITY = "## Identity & safety rules";
    public static final String HEADER_TARGETS = "## Conversation targets";
    public static final String HEADER_RAG = "## Scoped RAG";
    public static final String HEADER_ARCHITECTURE = "## Active Architecture facts";
    public static final String HEADER_WORK_LOGS = "## Recent work logs";
    public static final String HEADER_UI = "## UI surface";
    public static final String HEADER_SECRETS = "## Secrets & overreach warnings";

    private static final int WORK_LOG_LIMIT = 10;
    private static final int WORK_LOG_SUMMARY_CHARS = 200;

    private static final String IDENTITY_RULES = """
            You are ArchOps AI, an expert SRE assistant for a cloud-native operations platform.
            You help operators inspect and manage Linux server clusters, Kubernetes, Docker,
            and big-data stacks (Spark, Kafka, MinIO, Prometheus, Hadoop/HDFS/Hive).

            Knowledge policy:
            1. Prefer retrieve known architecture from the context snippet before probing hosts.
            2. L0 read-only diagnostics (df/free/uptime/ps/ls/…): do NOT write architecture.
            3. When you discover durable facts (roles like namenode/datanode/hive/spark, topology),
               you MUST call propose_architecture_update — never claim SSOT was updated directly.
            4. partitionKey rules: global | group:{id} | asset:{id}. Prefer asset:{id} or group:{id}
               when discoveries are scoped to conversation targets; use global only for fleet-wide facts.
            5. Architecture facts / RAG / Work Log are the source of truth — never treat asset Description as SSOT.

            Always prefer safe read-only diagnostics first. Respond in the same language the user writes in.
            """;

    private static final String SECRETS_WARNINGS = """
            - Never echo credentials, private keys, tokens, or secret env values into chat or proposals.
            - Do not expand tool scope beyond conversation targets and user ACL.
            - UI surface hints are advisory only; server targets and ACL are authoritative.
            - Do not dump entire architecture partitions; use facts and scoped RAG only.
            """;

    private final AssetService assetService;
    private final RagRetrievalService ragRetrievalService;
    private final PlatformAiSettingsService settingsService;
    private final ObjectProvider<ArchitectureViewService> architectureViewService;
    private final ObjectProvider<ArchitectureProperties> architectureProperties;
    private final WorkLogRepository workLogRepository;

    public AgentContextAssembler(
            AssetService assetService,
            RagRetrievalService ragRetrievalService,
            PlatformAiSettingsService settingsService,
            ObjectProvider<ArchitectureViewService> architectureViewService,
            ObjectProvider<ArchitectureProperties> architectureProperties,
            WorkLogRepository workLogRepository) {
        this.assetService = assetService;
        this.ragRetrievalService = ragRetrievalService;
        this.settingsService = settingsService;
        this.architectureViewService = architectureViewService;
        this.architectureProperties = architectureProperties;
        this.workLogRepository = workLogRepository;
    }

    @Transactional(readOnly = true)
    public String assemble(
            Long userId,
            List<Long> assetIds,
            List<Long> groupIds,
            String userQuery,
            Long conversationId,
            UiContext uiContext) {
        return assemble(userId, assetIds, groupIds, userQuery, conversationId, uiContext, null);
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
            List<Long> groupIds,
            String userQuery,
            Long conversationId,
            UiContext uiContext,
            Integer contextCharBudgetOverride) {
        List<Long> assets = assetIds != null ? assetIds : List.of();
        List<Long> groups = groupIds != null ? groupIds : List.of();

        StringBuilder sb = new StringBuilder();
        appendSection(sb, HEADER_IDENTITY, IDENTITY_RULES.trim());
        appendSection(sb, HEADER_TARGETS, formatTargets(assets, groups));
        appendSection(sb, HEADER_RAG, formatRag(userQuery, assets, groups));
        appendSection(sb, HEADER_ARCHITECTURE, formatArchitecture(assets, groups));
        appendSection(sb, HEADER_WORK_LOGS, formatWorkLogs(conversationId, assets, groups));
        String uiHint = formatUiContext(uiContext);
        if (uiHint != null && !uiHint.isBlank()) {
            appendSection(sb, HEADER_UI, uiHint);
        }
        appendSection(sb, HEADER_SECRETS, SECRETS_WARNINGS.trim());

        return truncate(sb.toString().trim(), contextCharBudgetOverride);
    }

    private String truncate(String result, Integer contextCharBudgetOverride) {
        int maxChars;
        if (contextCharBudgetOverride != null && contextCharBudgetOverride > 0) {
            maxChars = contextCharBudgetOverride;
        } else {
            maxChars = architectureProperties.stream()
                    .findFirst()
                    .map(ArchitectureProperties::getContextMaxChars)
                    .orElse(4000);
        }
        if (maxChars > 0 && result.length() > maxChars) {
            return result.substring(0, maxChars) + "…";
        }
        return result;
    }

    private static void appendSection(StringBuilder sb, String header, String body) {
        if (body == null || body.isBlank()) {
            sb.append(header).append("\n(none)\n\n");
            return;
        }
        sb.append(header).append('\n').append(body.trim()).append("\n\n");
    }

    private String formatTargets(List<Long> assetIds, List<Long> groupIds) {
        StringBuilder sb = new StringBuilder();
        if (groupIds != null && !groupIds.isEmpty()) {
            sb.append("Target groups: ").append(groupIds).append('\n');
        }
        if (assetIds == null || assetIds.isEmpty()) {
            sb.append("Active target assets: none. Ask the user to select target assets before running ssh_exec, "
                    + "or pass assetId explicitly after listing assets.");
            return sb.toString();
        }
        sb.append("Active target assets. When ssh_exec omits assetId, the command runs on ALL targets sequentially:\n");
        for (Long assetId : assetIds) {
            try {
                AssetResponse asset = assetService.get(assetId);
                sb.append("- id=").append(asset.id())
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

    private String formatRag(String userQuery, List<Long> assetIds, List<Long> groupIds) {
        try {
            if (!settingsService.getSettings().isRagEnabled()
                    || userQuery == null
                    || userQuery.isBlank()) {
                return "(RAG disabled or empty query)";
            }
        } catch (Exception ex) {
            return "(RAG settings unavailable)";
        }
        RagScope scope = buildScope(assetIds, groupIds);
        List<ScoredChunk> chunks = ragRetrievalService.retrieve(userQuery, scope);
        if (chunks.isEmpty()) {
            return "(no scoped hits)";
        }
        StringBuilder sb = new StringBuilder();
        for (ScoredChunk chunk : chunks) {
            sb.append("- [")
                    .append(chunk.sourceType().name())
                    .append(" score=")
                    .append(String.format(Locale.US, "%.2f", chunk.similarity()))
                    .append("] ")
                    .append(chunk.content())
                    .append('\n');
        }
        return sb.toString();
    }

    private String formatArchitecture(List<Long> assetIds, List<Long> groupIds) {
        ArchitectureViewService viewService = architectureViewService.getIfAvailable();
        if (viewService == null) {
            return "(architecture view unavailable)";
        }
        ArchitectureViewResponse view = viewService.buildView(assetIds, groupIds);
        // Use prompt snippet only — never assembledMarkdown / full partition dump / Description
        String snippet = viewService.toPromptSnippet(view);
        if (snippet == null || snippet.isBlank()) {
            return "(no active facts)";
        }
        return snippet;
    }

    private String formatWorkLogs(Long conversationId, List<Long> assetIds, List<Long> groupIds) {
        List<WorkLog> logs;
        if (conversationId != null) {
            logs = workLogRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        } else if (assetIds != null && !assetIds.isEmpty()) {
            logs = workLogRepository.findFiltered(null, assetIds.getFirst(), null);
        } else if (groupIds != null && !groupIds.isEmpty()) {
            logs = workLogRepository.findFiltered(null, null, groupIds.getFirst());
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

    private static RagScope buildScope(List<Long> assetIds, List<Long> groupIds) {
        List<String> keys = new ArrayList<>();
        keys.add(PartitionKeys.GLOBAL);
        if (assetIds != null) {
            for (Long id : assetIds) {
                keys.add(PartitionKeys.asset(id));
            }
        }
        if (groupIds != null) {
            for (Long id : groupIds) {
                keys.add(PartitionKeys.group(id));
            }
        }
        return new RagScope(
                assetIds != null ? assetIds : List.of(),
                groupIds != null ? groupIds : List.of(),
                keys);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
