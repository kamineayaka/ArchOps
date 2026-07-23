package com.archops.knowledge.retrieval;

import com.archops.ai.provider.service.PlatformAiSettingsService;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.indexing.EmbeddingException;
import com.archops.knowledge.indexing.EmbeddingProvider;
import com.archops.knowledge.indexing.EmbeddingProviderResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private final PlatformAiSettingsService settingsService;
    private final EmbeddingProviderResolver embeddingProviderResolver;
    private final KbChunkVectorRepository vectorRepository;
    private final AssetAclService assetAclService;
    private final RagMetrics ragMetrics;
    private final ObjectMapper objectMapper;

    public RagRetrievalService(
            PlatformAiSettingsService settingsService,
            EmbeddingProviderResolver embeddingProviderResolver,
            KbChunkVectorRepository vectorRepository,
            AssetAclService assetAclService,
            RagMetrics ragMetrics,
            ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.embeddingProviderResolver = embeddingProviderResolver;
        this.vectorRepository = vectorRepository;
        this.assetAclService = assetAclService;
        this.ragMetrics = ragMetrics;
        this.objectMapper = objectMapper;
    }

    public List<ScoredChunk> retrieve(String query) {
        return retrieve(query, null, null, null);
    }

    public List<ScoredChunk> retrieve(String query, Integer topKOverride) {
        return retrieve(query, null, topKOverride, null, null);
    }

    public List<ScoredChunk> retrieve(String query, RagScope scope) {
        return retrieve(query, scope, null, null, null);
    }

    public List<ScoredChunk> retrieve(
            String query, RagScope scope, Long userId, Collection<String> roles) {
        return retrieve(query, scope, null, userId, roles);
    }

    public List<ScoredChunk> retrieve(
            String query,
            RagScope scope,
            Integer topKOverride,
            Long userId,
            Collection<String> roles) {
        var settings = settingsService.getSettings();
        if (!settings.isRagEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        RagScope effectiveScope = scope;
        if (userId != null || (roles != null && !roles.isEmpty())) {
            effectiveScope = assetAclService.intersectScope(userId, roles, scope);
        }
        int topK = topKOverride != null && topKOverride > 0 ? topKOverride : settings.getRagTopK();
        // Fetch extra candidates when scoping so filter still yields ~topK
        int fetchK = (effectiveScope != null && !effectiveScope.isEmpty()) ? Math.max(topK * 4, 20) : topK;
        try {
            EmbeddingProvider provider = embeddingProviderResolver.active();
            float[] queryVector = provider.embed(query.strip());
            List<ScoredChunk> scored = vectorRepository.searchSimilar(
                    queryVector,
                    fetchK,
                    settings.getRagMinSimilarity());
            List<ScoredChunk> filtered = filterByScope(scored, effectiveScope, topK);
            ragMetrics.recordHits(filtered.size());
            return filtered;
        } catch (EmbeddingException ex) {
            log.warn("RAG retrieval skipped: {}", ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("RAG retrieval failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ScoredChunk> filterByScope(List<ScoredChunk> scored, RagScope scope, int topK) {
        if (scope == null || scope.isEmpty()) {
            return scored.size() <= topK ? scored : scored.subList(0, topK);
        }
        Set<Long> assetIds = scope.assetIds() != null ? new HashSet<>(scope.assetIds()) : Set.of();
        Set<Long> groupIds = scope.groupIds() != null ? new HashSet<>(scope.groupIds()) : Set.of();
        Set<String> partitionKeys =
                scope.partitionKeys() != null ? new HashSet<>(scope.partitionKeys()) : Set.of();

        List<ScoredChunk> out = new ArrayList<>();
        for (ScoredChunk chunk : scored) {
            if (matchesScope(chunk.metadata(), assetIds, groupIds, partitionKeys)) {
                out.add(chunk);
                if (out.size() >= topK) {
                    break;
                }
            }
        }
        return out;
    }

    private boolean matchesScope(
            String metadataJson, Set<Long> assetIds, Set<Long> groupIds, Set<String> partitionKeys) {
        Map<String, Object> meta = parseMetadata(metadataJson);
        if (meta.isEmpty() && (assetIds.isEmpty() && groupIds.isEmpty() && partitionKeys.isEmpty())) {
            return true;
        }
        if (!partitionKeys.isEmpty()) {
            Object pk = meta.get("partition_key");
            if (pk == null) {
                pk = meta.get("partitionKey");
            }
            if (pk != null && partitionKeys.contains(String.valueOf(pk))) {
                return true;
            }
        }
        if (!assetIds.isEmpty()) {
            Long assetId = asLong(meta.get("asset_id"));
            if (assetId == null) {
                assetId = asLong(meta.get("assetId"));
            }
            if (assetId != null && assetIds.contains(assetId)) {
                return true;
            }
            Object assetIdsMeta = meta.get("assetIds");
            if (assetIdsMeta instanceof Collection<?> col) {
                for (Object o : col) {
                    Long id = asLong(o);
                    if (id != null && assetIds.contains(id)) {
                        return true;
                    }
                }
            }
        }
        if (!groupIds.isEmpty()) {
            Long groupId = asLong(meta.get("group_id"));
            if (groupId == null) {
                groupId = asLong(meta.get("groupId"));
            }
            if (groupId != null && groupIds.contains(groupId)) {
                return true;
            }
        }
        // If scope dimensions present but none matched → exclude
        return false;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
