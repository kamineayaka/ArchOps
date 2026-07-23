package com.archops.knowledge.repository;

import com.archops.knowledge.domain.WorkLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {
    List<WorkLog> findTop20ByOrderByCreatedAtDesc();

    List<WorkLog> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    @Query(value = """
            SELECT * FROM work_log w
            WHERE (:conversationId IS NULL OR w.conversation_id = :conversationId)
              AND (:assetId IS NULL OR w.asset_ids @> jsonb_build_array(CAST(:assetId AS bigint)))
              AND (:groupId IS NULL OR w.group_ids @> jsonb_build_array(CAST(:groupId AS bigint)))
            ORDER BY w.created_at DESC
            LIMIT 100
            """, nativeQuery = true)
    List<WorkLog> findFiltered(
            @Param("conversationId") Long conversationId,
            @Param("assetId") Long assetId,
            @Param("groupId") Long groupId);
}
