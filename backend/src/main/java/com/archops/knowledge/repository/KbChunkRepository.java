package com.archops.knowledge.repository;

import com.archops.knowledge.domain.KbChunk;
import com.archops.knowledge.domain.KnowledgeSourceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM KbChunk c WHERE c.sourceType = :sourceType AND c.sourceId = :sourceId")
    void deleteBySource(@Param("sourceType") KnowledgeSourceType sourceType, @Param("sourceId") Long sourceId);

    long countBySourceType(KnowledgeSourceType sourceType);

    List<KbChunk> findBySourceTypeAndSourceIdOrderByChunkIndexAsc(
            KnowledgeSourceType sourceType, Long sourceId);

    @Query("SELECT DISTINCT c.sourceId FROM KbChunk c WHERE c.sourceType = :sourceType AND c.sourceId IS NOT NULL")
    List<Long> findDistinctSourceIds(@Param("sourceType") KnowledgeSourceType sourceType);
}