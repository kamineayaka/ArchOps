package com.archops.knowledge.architecture.dto;

import com.archops.knowledge.architecture.domain.ProposalStatus;
import java.time.Instant;

public record ProposalResponse(
        Long id,
        String partitionKey,
        ProposalStatus status,
        String summary,
        String diffJson,
        String factOps,
        String evidence,
        String risk,
        Double confidence,
        Long requesterId,
        Long reviewerId,
        Long conversationId,
        Long baseVersion,
        Long relatedApprovalId,
        Instant createdAt,
        Instant decidedAt) {}
