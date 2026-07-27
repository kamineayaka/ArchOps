package com.archops.knowledge.indexing;

import com.archops.ai.provider.service.PlatformAiSettingsService;
import com.archops.common.config.RagProperties;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import com.archops.knowledge.architecture.repository.ArchitecturePartitionRepository;
import com.archops.knowledge.architecture.repository.ArchitectureRevisionRepository;
import com.archops.knowledge.domain.ArchitectureSnapshot;
import com.archops.knowledge.domain.KnowledgeSourceType;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.repository.ArchitectureSnapshotRepository;
import com.archops.knowledge.repository.KbChunkRepository;
import com.archops.knowledge.repository.WorkLogRepository;
import com.archops.knowledge.retrieval.KbChunkVectorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIndexingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);
    private static final int EMBED_BATCH_SIZE = 16;

    private final PlatformAiSettingsService settingsService;
    private final RagProperties ragProperties;
    private final TextChunker textChunker;
    private final EmbeddingProviderResolver embeddingProviderResolver;
    private final KbChunkRepository kbChunkRepository;
    private final KbChunkVectorRepository vectorRepository;
    private final ArchitectureSnapshotRepository snapshotRepository;
    private final WorkLogRepository workLogRepository;
    private final ArchitecturePartitionRepository partitionRepository;
    private final ArchitectureRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public KnowledgeIndexingService(
            PlatformAiSettingsService settingsService,
            RagProperties ragProperties,
            TextChunker textChunker,
            EmbeddingProviderResolver embeddingProviderResolver,
            KbChunkRepository kbChunkRepository,
            KbChunkVectorRepository vectorRepository,
            ArchitectureSnapshotRepository snapshotRepository,
            WorkLogRepository workLogRepository,
            ArchitecturePartitionRepository partitionRepository,
            ArchitectureRevisionRepository revisionRepository,
            ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.ragProperties = ragProperties;
        this.textChunker = textChunker;
        this.embeddingProviderResolver = embeddingProviderResolver;
        this.kbChunkRepository = kbChunkRepository;
        this.vectorRepository = vectorRepository;
        this.snapshotRepository = snapshotRepository;
        this.workLogRepository = workLogRepository;
        this.partitionRepository = partitionRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
    }

    @Async("ragTaskExecutor")
    public void scheduleIndexArchitecture(Long snapshotId) {
        if (!settingsService.getSettings().isRagEnabled()) {
            return;
        }
        try {
            snapshotRepository.findById(snapshotId).ifPresent(this::indexArchitecture);
        } catch (Exception ex) {
            log.warn("Async architecture indexing failed for id={}: {}", snapshotId, ex.getMessage());
        }
    }

    @Async("ragTaskExecutor")
    public void scheduleIndexWorkLog(Long workLogId) {
        if (!settingsService.getSettings().isRagEnabled()) {
            return;
        }
        try {
            workLogRepository.findById(workLogId).ifPresent(this::indexWorkLog);
        } catch (Exception ex) {
            log.warn("Async work-log indexing failed for id={}: {}", workLogId, ex.getMessage());
        }
    }

    @Transactional
    public int indexArchitecture(ArchitectureSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append("Architecture snapshot v").append(snapshot.getVersion()).append('\n');
        if (snapshot.getSummary() != null) {
            text.append(snapshot.getSummary()).append('\n');
        }
        if (snapshot.getContent() != null) {
            text.append(snapshot.getContent());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", snapshot.getVersion());
        metadata.put("createdAt", snapshot.getCreatedAt().toString());
        metadata.put("partition_key", PartitionKeys.GLOBAL);
        return indexDocument(KnowledgeSourceType.ARCHITECTURE, snapshot.getId(), text.toString(), metadata);
    }

    @Transactional
    public int indexPartitionRevision(ArchitecturePartition partition, ArchitectureRevision revision) {
        StringBuilder text = new StringBuilder();
        text.append("Architecture partition ")
                .append(partition.getPartitionKey())
                .append(" v")
                .append(revision.getVersion())
                .append('\n');
        if (revision.getSummary() != null) {
            text.append(revision.getSummary()).append('\n');
        }
        if (revision.getBodyMd() != null) {
            text.append(revision.getBodyMd());
        }
        // Skip empty / fact-only partitions — structured facts are injected via Hybrid RAG, not vectors
        String body = text.toString().trim();
        if (body.length() < 40) {
            kbChunkRepository.deleteBySource(KnowledgeSourceType.ARCHITECTURE, revision.getId());
            return 0;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("partition_key", partition.getPartitionKey());
        metadata.put("version", revision.getVersion());
        metadata.put("revision_id", revision.getId());
        enrichAssetScopeMetadata(metadata, partition.getPartitionKey());
        return indexDocument(KnowledgeSourceType.ARCHITECTURE, revision.getId(), text.toString(), metadata);
    }

    @Transactional
    public int reindexPartition(String partitionKey) {
        if (!settingsService.getSettings().isRagEnabled()) {
            return 0;
        }
        PartitionKeys.validate(partitionKey);
        ArchitecturePartition partition = partitionRepository.findByPartitionKey(partitionKey).orElse(null);
        if (partition == null) {
            return 0;
        }
        ArchitectureRevision latest = revisionRepository
                .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                .orElse(null);
        if (latest == null) {
            return 0;
        }
        // Drop previous chunks for this revision source id, then reindex latest
        return indexPartitionRevision(partition, latest);
    }

    @Async("ragTaskExecutor")
    public void scheduleReindexPartition(String partitionKey) {
        try {
            reindexPartition(partitionKey);
        } catch (Exception ex) {
            log.warn("Async partition reindex failed for {}: {}", partitionKey, ex.getMessage());
        }
    }

    @Transactional
    public int indexWorkLog(WorkLog workLog) {
        StringBuilder text = new StringBuilder();
        text.append('[').append(workLog.getLogType()).append("] ");
        if (workLog.getActorName() != null) {
            text.append(workLog.getActorName()).append(": ");
        }
        text.append(workLog.getSummary());
        if (workLog.getDiff() != null && !workLog.getDiff().isBlank() && !"{}".equals(workLog.getDiff())) {
            text.append("\nDetails: ").append(workLog.getDiff());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("logType", workLog.getLogType());
        metadata.put("actorName", workLog.getActorName() != null ? workLog.getActorName() : "");
        metadata.put("createdAt", workLog.getCreatedAt().toString());
        if (workLog.getConversationId() != null) {
            metadata.put("conversationId", workLog.getConversationId());
        }
        if (workLog.getLevel() != null) {
            metadata.put("level", workLog.getLevel());
        }
        if (workLog.getAssetIds() != null && !workLog.getAssetIds().isEmpty()) {
            metadata.put("assetIds", workLog.getAssetIds());
            metadata.put("asset_id", workLog.getAssetIds().getFirst());
            metadata.put("partition_key", PartitionKeys.asset(workLog.getAssetIds().getFirst()));
        }
        if (workLog.getGroupIds() != null && !workLog.getGroupIds().isEmpty()) {
            metadata.put("groupIds", workLog.getGroupIds());
            metadata.put("group_id", workLog.getGroupIds().getFirst());
        }
        return indexDocument(KnowledgeSourceType.WORK_LOG, workLog.getId(), text.toString(), metadata);
    }

    @Transactional
    public int indexManualDocument(Long documentId, String title, String content) {
        String text = title + "\n" + content;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("partition_key", PartitionKeys.GLOBAL);
        return indexDocument(KnowledgeSourceType.MANUAL, documentId, text, metadata);
    }

    @Transactional
    public ReindexResult reindexAll() {
        if (!settingsService.getSettings().isRagEnabled()) {
            return new ReindexResult(0, 0, "RAG is disabled");
        }

        // Preserve MANUAL docs across wipe (no separate source table)
        Map<Long, String> manualDocs = new HashMap<>();
        Map<Long, String> manualTitles = new HashMap<>();
        for (Long sourceId : kbChunkRepository.findDistinctSourceIds(KnowledgeSourceType.MANUAL)) {
            List<com.archops.knowledge.domain.KbChunk> chunks =
                    kbChunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(
                            KnowledgeSourceType.MANUAL, sourceId);
            if (chunks.isEmpty()) {
                continue;
            }
            StringBuilder joined = new StringBuilder();
            for (var chunk : chunks) {
                if (!joined.isEmpty()) {
                    joined.append('\n');
                }
                joined.append(chunk.getContent());
            }
            manualDocs.put(sourceId, joined.toString());
            manualTitles.put(sourceId, "manual-" + sourceId);
            try {
                var meta = objectMapper.readTree(chunks.getFirst().getMetadata());
                if (meta.has("title") && !meta.get("title").asText("").isBlank()) {
                    manualTitles.put(sourceId, meta.get("title").asText());
                }
            } catch (Exception ignored) {
                // keep default title
            }
        }

        vectorRepository.deleteAllChunks();
        int architectureChunks = 0;
        int workLogChunks = 0;
        int partitionChunks = 0;
        int manualChunks = 0;
        int sources = 0;

        for (ArchitectureSnapshot snapshot : snapshotRepository.findAll()) {
            architectureChunks += indexArchitecture(snapshot);
            sources++;
        }
        for (ArchitecturePartition partition : partitionRepository.findAll()) {
            ArchitectureRevision latest = revisionRepository
                    .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                    .orElse(null);
            if (latest == null) {
                continue;
            }
            partitionChunks += indexPartitionRevision(partition, latest);
            sources++;
        }
        for (WorkLog workLog : workLogRepository.findAll()) {
            workLogChunks += indexWorkLog(workLog);
            sources++;
        }
        for (Map.Entry<Long, String> entry : manualDocs.entrySet()) {
            Long id = entry.getKey();
            String full = entry.getValue();
            String title = manualTitles.getOrDefault(id, "manual-" + id);
            // full text already includes title line from prior index — re-embed as content body
            manualChunks += indexManualDocument(id, title, full);
            sources++;
        }

        int total = architectureChunks + workLogChunks + partitionChunks + manualChunks;
        log.info(
                "RAG reindex complete: {} chunks (architecture={}, partitions={}, work_log={}, manual={})",
                total,
                architectureChunks,
                partitionChunks,
                workLogChunks,
                manualChunks);
        return new ReindexResult(total, sources, "ok");
    }

    private void enrichAssetScopeMetadata(Map<String, Object> metadata, String partitionKey) {
        if (partitionKey == null) {
            return;
        }
        if (partitionKey.startsWith("asset:")) {
            String ref = partitionKey.substring("asset:".length());
            try {
                metadata.put("asset_id", Long.parseLong(ref));
            } catch (NumberFormatException ex) {
                metadata.put("element_id", ref);
            }
        }
        if (partitionKey.startsWith("group:")) {
            try {
                metadata.put("group_id", Long.parseLong(partitionKey.substring("group:".length())));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        if (partitionKey.startsWith("cluster:")) {
            metadata.put("element_id", partitionKey.substring("cluster:".length()));
        }
        if (partitionKey.startsWith("tag:")) {
            metadata.put("tag_slug", partitionKey.substring("tag:".length()));
        }
    }

    private int indexDocument(
            KnowledgeSourceType sourceType,
            Long sourceId,
            String text,
            Map<String, Object> metadata) {
        if (!settingsService.getSettings().isRagEnabled()) {
            return 0;
        }
        kbChunkRepository.deleteBySource(sourceType, sourceId);
        List<String> chunks = textChunker.chunk(text, ragProperties.chunkSize(), ragProperties.chunkOverlap());
        if (chunks.isEmpty()) {
            return 0;
        }

        EmbeddingProvider provider = embeddingProviderResolver.active();
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new EmbeddingException("Failed to serialize chunk metadata", ex);
        }
        int indexed = 0;

        for (int offset = 0; offset < chunks.size(); offset += EMBED_BATCH_SIZE) {
            List<String> batch = chunks.subList(offset, Math.min(offset + EMBED_BATCH_SIZE, chunks.size()));
            List<float[]> embeddings = provider.embedBatch(batch);
            for (int i = 0; i < batch.size(); i++) {
                int chunkIndex = offset + i;
                vectorRepository.insertChunk(
                        sourceType,
                        sourceId,
                        chunkIndex,
                        batch.get(i),
                        metadataJson,
                        embeddings.get(i));
                indexed++;
            }
        }
        log.debug("Indexed {} chunks for {}:{}", indexed, sourceType, sourceId);
        return indexed;
    }

    public record ReindexResult(int totalChunks, int sourcesProcessed, String status) {}
}
