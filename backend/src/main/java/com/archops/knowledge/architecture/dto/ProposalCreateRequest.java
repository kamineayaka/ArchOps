package com.archops.knowledge.architecture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProposalCreateRequest(
        @NotBlank String partitionKey,
        String summary,
        String factOpsJson,
        @Valid List<FactOpRequest> factOps,
        /** GraphChangeSet JSON; when present with ops, Merge uses GraphMergeEngine. */
        String changeSetJson,
        String planJson,
        String evidenceJson,
        String risk,
        Double confidence,
        Long conversationId,
        Long relatedApprovalId,
        @NotNull Long baseVersion,
        Long baseGraphVersion,
        String source) {}
