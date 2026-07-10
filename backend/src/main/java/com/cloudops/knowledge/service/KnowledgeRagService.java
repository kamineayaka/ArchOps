package com.cloudops.knowledge.service;

import com.cloudops.ai.provider.repository.AiProviderRepository;
import com.cloudops.ai.provider.service.PlatformAiSettingsService;
import com.cloudops.knowledge.domain.KnowledgeSourceType;
import com.cloudops.knowledge.dto.IndexStatsResponse;
import com.cloudops.knowledge.dto.ManualDocumentResponse;
import com.cloudops.knowledge.dto.RagChunkResponse;
import com.cloudops.knowledge.dto.ReindexResponse;
import com.cloudops.knowledge.indexing.KnowledgeIndexingService;
import com.cloudops.knowledge.repository.KbChunkRepository;
import com.cloudops.knowledge.retrieval.RagRetrievalService;
import com.cloudops.knowledge.retrieval.ScoredChunk;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeRagService {

    private static final AtomicLong MANUAL_DOC_SEQ = new AtomicLong(System.currentTimeMillis());

    private final PlatformAiSettingsService settingsService;
    private final AiProviderRepository providerRepository;
    private final RagRetrievalService ragRetrievalService;
    private final KnowledgeIndexingService indexingService;
    private final KbChunkRepository kbChunkRepository;

    public KnowledgeRagService(
            PlatformAiSettingsService settingsService,
            AiProviderRepository providerRepository,
            RagRetrievalService ragRetrievalService,
            KnowledgeIndexingService indexingService,
            KbChunkRepository kbChunkRepository) {
        this.settingsService = settingsService;
        this.providerRepository = providerRepository;
        this.ragRetrievalService = ragRetrievalService;
        this.indexingService = indexingService;
        this.kbChunkRepository = kbChunkRepository;
    }

    @Transactional(readOnly = true)
    public List<RagChunkResponse> search(String query, Integer topK) {
        return ragRetrievalService.retrieve(query, topK).stream()
                .map(c -> new RagChunkResponse(
                        c.id(), c.sourceType(), c.sourceId(), c.chunkIndex(), c.content(), c.similarity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public IndexStatsResponse stats() {
        var settings = settingsService.getSettings();
        String embeddingLabel = "none";
        if (settings.getDefaultEmbeddingProviderId() != null) {
            embeddingLabel = providerRepository.findById(settings.getDefaultEmbeddingProviderId())
                    .map(p -> p.getName() + " / " + p.getEmbeddingModel())
                    .orElse("unknown");
        }
        return new IndexStatsResponse(
                settings.isRagEnabled(),
                embeddingLabel,
                kbChunkRepository.count(),
                kbChunkRepository.countBySourceType(KnowledgeSourceType.ARCHITECTURE),
                kbChunkRepository.countBySourceType(KnowledgeSourceType.WORK_LOG),
                kbChunkRepository.countBySourceType(KnowledgeSourceType.MANUAL));
    }

    @Transactional
    public ReindexResponse reindexAll() {
        KnowledgeIndexingService.ReindexResult result = indexingService.reindexAll();
        return new ReindexResponse(result.totalChunks(), result.sourcesProcessed(), result.status());
    }

    @Transactional
    public ManualDocumentResponse indexManualDocument(String title, String content) {
        long documentId = MANUAL_DOC_SEQ.incrementAndGet();
        int chunks = indexingService.indexManualDocument(documentId, title, content);
        return new ManualDocumentResponse(documentId, chunks);
    }
}
