package com.archops.approval.repository;

import com.archops.approval.domain.ExecutionGrant;
import com.archops.approval.domain.RiskLevel;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionGrantRepository extends JpaRepository<ExecutionGrant, Long> {

    @Query("""
            SELECT g FROM ExecutionGrant g
            WHERE g.userId = :userId
              AND g.conversationId = :conversationId
              AND g.toolName = :toolName
              AND g.riskLevel = :riskLevel
              AND g.expiresAt > :now
              AND (g.assetId IS NULL OR g.assetId = :assetId)
            ORDER BY g.createdAt DESC
            """)
    List<ExecutionGrant> findActiveMatches(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("toolName") String toolName,
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("assetId") Long assetId,
            @Param("now") Instant now);

    @Query("""
            SELECT g FROM ExecutionGrant g
            WHERE g.userId = :userId
              AND g.conversationId = :conversationId
              AND g.toolName = :toolName
              AND g.riskLevel = :riskLevel
              AND g.expiresAt > :now
              AND g.assetId IS NULL
            ORDER BY g.createdAt DESC
            """)
    List<ExecutionGrant> findActiveConversationWideMatches(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("toolName") String toolName,
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("now") Instant now);

    @Query("""
            SELECT g FROM ExecutionGrant g
            WHERE g.userId = :userId
              AND g.conversationId = :conversationId
              AND g.expiresAt > :now
            ORDER BY g.createdAt DESC
            """)
    List<ExecutionGrant> findActiveForConversation(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("now") Instant now);
}
