package com.archops.knowledge.architecture.dto;

import com.archops.knowledge.architecture.domain.ProposalStatus;
import java.time.Instant;

public record ProposalResponse(
        Long id,
        String partitionKey,
        String scopeKind,
        String scopeRef,
        ProposalStatus status,
        String summary,
        String diffJson,
        String factOps,
        String changeSet,
        String planJson,
        String evidence,
        String risk,
        Double confidence,
        Long requesterId,
        Long reviewerId,
        Long conversationId,
        Long baseVersion,
        Long baseGraphVersion,
        Long mergedGraphVersion,
        String source,
        Long relatedApprovalId,
        String conflictDetail,
        Instant createdAt,
        Instant decidedAt) {}
