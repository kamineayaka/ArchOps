package com.archops.knowledge.hybrid;

import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.dto.ArchitectureViewResponse;
import com.archops.knowledge.architecture.service.ArchitectureViewService;
import com.archops.knowledge.retrieval.RagRetrievalService;
import com.archops.knowledge.retrieval.RagScope;
import com.archops.knowledge.retrieval.ScoredChunk;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Orchestrates graph neighborhood + architecture facts + scoped vector text memory.
 */
@Service
public class HybridRetrievalService {

    private static final Pattern ASSET_ID_IN_QUERY = Pattern.compile("\\b(?:asset[=:#\\s]|id[=:#\\s])(\\d{1,18})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_ID = Pattern.compile("\\b(\\d{4,18})\\b");

    private final GraphContextRetriever graphContextRetriever;
    private final ObjectProvider<ArchitectureViewService> architectureViewService;
    private final RagRetrievalService ragRetrievalService;
    private final AssetService assetService;
    private final AssetAclService assetAclService;
    private final HybridRetrievalMetrics metrics;

    public HybridRetrievalService(
            GraphContextRetriever graphContextRetriever,
            ObjectProvider<ArchitectureViewService> architectureViewService,
            RagRetrievalService ragRetrievalService,
            AssetService assetService,
            AssetAclService assetAclService,
            HybridRetrievalMetrics metrics) {
        this.graphContextRetriever = graphContextRetriever;
        this.architectureViewService = architectureViewService;
        this.ragRetrievalService = ragRetrievalService;
        this.assetService = assetService;
        this.assetAclService = assetAclService;
        this.metrics = metrics;
    }

    public HybridRetrievalResult retrieve(
            String userQuery,
            List<Long> targetAssetIds,
            Long userId,
            Collection<String> roles) {
        List<Long> rawSeeds = resolveSeedAssetIds(userQuery, targetAssetIds);
        // ACL-filter seeds before graph/fact expansion so inaccessible assets never enter the prompt.
        List<Long> seeds = assetAclService.filterAssetIds(userId, roles, rawSeeds);

        GraphContextRetriever.NeighborhoodResult neighborhood = graphContextRetriever.neighborhood(seeds);

        Set<Long> scopedAssets = new LinkedHashSet<>(seeds);
        if (neighborhood.available() && neighborhood.relatedPgAssetIds() != null) {
            scopedAssets.addAll(assetAclService.filterAssetIds(userId, roles, neighborhood.relatedPgAssetIds()));
        }

        Set<String> partitionKeys = new LinkedHashSet<>();
        if (assetAclService.canAccessPartition(userId, roles, PartitionKeys.GLOBAL)) {
            partitionKeys.add(PartitionKeys.GLOBAL);
        }
        for (Long id : scopedAssets) {
            String assetKey = PartitionKeys.asset(id);
            if (assetAclService.canAccessPartition(userId, roles, assetKey)) {
                partitionKeys.add(assetKey);
            }
            try {
                AssetResponse asset = assetService.get(id);
                if (asset.elementId() != null) {
                    String elementKey = PartitionKeys.assetElement(asset.elementId());
                    if (assetAclService.canAccessPartition(userId, roles, elementKey)) {
                        partitionKeys.add(elementKey);
                    }
                    if ("CLUSTER".equalsIgnoreCase(String.valueOf(asset.kind()))) {
                        String clusterKey = PartitionKeys.cluster(asset.elementId());
                        if (assetAclService.canAccessPartition(userId, roles, clusterKey)) {
                            partitionKeys.add(clusterKey);
                        }
                    }
                }
            } catch (Exception ignored) {
                // asset may be soft-deleted / inaccessible
            }
        }
        if (neighborhood.suggestedPartitionKeys() != null) {
            for (String key : neighborhood.suggestedPartitionKeys()) {
                if (assetAclService.canAccessPartition(userId, roles, key)) {
                    partitionKeys.add(key);
                }
            }
        }

        String factsText = formatFacts(List.copyOf(scopedAssets), List.copyOf(partitionKeys));
        int factHits = countFactLines(factsText);

        RagScope scope = new RagScope(List.copyOf(scopedAssets), List.copyOf(partitionKeys));
        List<ScoredChunk> chunks = ragRetrievalService.retrieve(userQuery, scope, userId, roles);
        String textMemory = formatVectorMemory(chunks);

        metrics.record(neighborhood.nodeCount(), factHits, chunks.size());

        return new HybridRetrievalResult(
                neighborhood.promptText(),
                factsText,
                textMemory,
                List.copyOf(scopedAssets),
                List.copyOf(partitionKeys),
                neighborhood.nodeCount(),
                factHits,
                chunks.size(),
                neighborhood.available());
    }

    /** Seeds = conversation targets ∪ ids mentioned in the query. */
    List<Long> resolveSeedAssetIds(String userQuery, List<Long> targetAssetIds) {
        LinkedHashSet<Long> seeds = new LinkedHashSet<>();
        if (targetAssetIds != null) {
            for (Long id : targetAssetIds) {
                if (id != null && id > 0) {
                    seeds.add(id);
                }
            }
        }
        if (userQuery != null && !userQuery.isBlank()) {
            Matcher m = ASSET_ID_IN_QUERY.matcher(userQuery);
            while (m.find()) {
                try {
                    seeds.add(Long.parseLong(m.group(1)));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
            // Only accept bare numeric tokens that already appear in targets (avoid random year/port noise)
            if (!seeds.isEmpty()) {
                Matcher bare = BARE_ID.matcher(userQuery);
                Set<Long> allowed = Set.copyOf(seeds);
                while (bare.find()) {
                    try {
                        long id = Long.parseLong(bare.group(1));
                        if (allowed.contains(id)) {
                            seeds.add(id);
                        }
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            }
        }
        return List.copyOf(seeds);
    }

    private String formatFacts(List<Long> assetIds, List<String> partitionKeys) {
        ArchitectureViewService viewService = architectureViewService.getIfAvailable();
        if (viewService == null) {
            return "(architecture view unavailable)";
        }
        ArchitectureViewResponse view = viewService.buildView(assetIds, partitionKeys);
        String snippet = viewService.toPromptSnippet(view);
        if (snippet == null || snippet.isBlank()) {
            return "(no active facts)";
        }
        return snippet;
    }

    private static String formatVectorMemory(List<ScoredChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "(no scoped text-memory hits)";
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
        return sb.toString().trim();
    }

    private static int countFactLines(String factsText) {
        if (factsText == null || factsText.isBlank() || factsText.startsWith("(no")) {
            return 0;
        }
        int count = 0;
        for (String line : factsText.split("\n")) {
            if (line.startsWith("- [")) {
                count++;
            }
        }
        return count;
    }
}
