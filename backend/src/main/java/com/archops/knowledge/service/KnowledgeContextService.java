package com.archops.knowledge.service;

import com.archops.ai.provider.service.PlatformAiSettingsService;
import com.archops.knowledge.architecture.ArchitectureProperties;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.dto.ArchitectureViewResponse;
import com.archops.knowledge.architecture.service.ArchitectureViewService;
import com.archops.knowledge.domain.ArchitectureSnapshot;
import com.archops.knowledge.retrieval.RagRetrievalService;
import com.archops.knowledge.retrieval.RagScope;
import com.archops.knowledge.retrieval.ScoredChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeContextService {

    private final PlatformAiSettingsService settingsService;
    private final RagRetrievalService ragRetrievalService;
    private final ObjectProvider<ArchitectureViewService> architectureViewService;
    private final ObjectProvider<ArchitectureProperties> architectureProperties;
    private final ObjectProvider<com.archops.knowledge.repository.ArchitectureSnapshotRepository> snapshotRepository;

    public KnowledgeContextService(
            PlatformAiSettingsService settingsService,
            RagRetrievalService ragRetrievalService,
            ObjectProvider<ArchitectureViewService> architectureViewService,
            ObjectProvider<ArchitectureProperties> architectureProperties,
            ObjectProvider<com.archops.knowledge.repository.ArchitectureSnapshotRepository> snapshotRepository) {
        this.settingsService = settingsService;
        this.ragRetrievalService = ragRetrievalService;
        this.architectureViewService = architectureViewService;
        this.architectureProperties = architectureProperties;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional(readOnly = true)
    public String buildContextSnippet(String userQuery) {
        return buildContextSnippet(userQuery, RagScope.empty());
    }

    @Transactional(readOnly = true)
    public String buildContextSnippet(String userQuery, RagScope scope) {
        int maxChars = architectureProperties.stream()
                .findFirst()
                .map(ArchitectureProperties::getContextMaxChars)
                .orElse(4000);

        StringBuilder sb = new StringBuilder();

        ArchitectureViewService viewService = architectureViewService.getIfAvailable();
        if (viewService != null) {
            List<Long> assetIds = scope != null && scope.assetIds() != null ? scope.assetIds() : List.of();
            List<Long> groupIds = scope != null && scope.groupIds() != null ? scope.groupIds() : List.of();
            ArchitectureViewResponse view = viewService.buildView(assetIds, groupIds);
            String archSnippet = viewService.toPromptSnippet(view);
            if (archSnippet != null && !archSnippet.isBlank()) {
                sb.append(archSnippet).append('\n');
            }
        } else {
            sb.append("## Current Architecture\n");
            var snapshots = snapshotRepository.getIfAvailable();
            if (snapshots != null) {
                snapshots.findTopByOrderByVersionDesc()
                        .ifPresentOrElse(
                                s -> sb.append(shortSummary(s)).append('\n'),
                                () -> sb.append("No architecture snapshot recorded yet.\n"));
            } else {
                sb.append("No architecture view available.\n");
            }
        }

        if (settingsService.getSettings().isRagEnabled() && userQuery != null && !userQuery.isBlank()) {
            RagScope effective = enrichScopeWithPartitions(scope);
            List<ScoredChunk> chunks = ragRetrievalService.retrieve(userQuery, effective);
            if (!chunks.isEmpty()) {
                sb.append("\n## Relevant Knowledge (scoped RAG)\n");
                for (ScoredChunk chunk : chunks) {
                    sb.append("- [")
                            .append(chunk.sourceType().name())
                            .append(" score=")
                            .append(String.format(Locale.US, "%.2f", chunk.similarity()))
                            .append("] ")
                            .append(chunk.content())
                            .append('\n');
                }
            }
        }

        String result = sb.toString().trim();
        if (maxChars > 0 && result.length() > maxChars) {
            return result.substring(0, maxChars) + "…";
        }
        return result;
    }

    private static RagScope enrichScopeWithPartitions(RagScope scope) {
        if (scope == null || scope.isEmpty()) {
            return new RagScope(List.of(), List.of(), List.of(PartitionKeys.GLOBAL));
        }
        List<String> keys = new ArrayList<>();
        if (scope.partitionKeys() != null) {
            keys.addAll(scope.partitionKeys());
        }
        if (scope.assetIds() != null) {
            for (Long id : scope.assetIds()) {
                keys.add(PartitionKeys.asset(id));
            }
        }
        if (scope.groupIds() != null) {
            for (Long id : scope.groupIds()) {
                keys.add(PartitionKeys.group(id));
            }
        }
        if (!keys.contains(PartitionKeys.GLOBAL)) {
            keys.add(0, PartitionKeys.GLOBAL);
        }
        return new RagScope(scope.assetIds(), scope.groupIds(), keys);
    }

    private static String shortSummary(ArchitectureSnapshot s) {
        if (s.getSummary() != null && !s.getSummary().isBlank()) {
            String text = s.getSummary().trim();
            return text.length() > 500 ? text.substring(0, 500) + "…" : text;
        }
        return "Architecture snapshot v" + s.getVersion();
    }
}
