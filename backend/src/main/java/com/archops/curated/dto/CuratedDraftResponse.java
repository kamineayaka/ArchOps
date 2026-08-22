package com.archops.curated.dto;

import com.archops.common.api.BranchSelectionResult;
import com.archops.curated.domain.CuratedDraftItemKind;
import com.archops.curated.domain.CuratedDraftItemStatus;
import com.archops.curated.domain.CuratedDraftOrigin;
import com.archops.curated.domain.CuratedDraftStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CuratedDraftResponse(
        String id,
        String conflictId,
        String diagnosisId,
        String selectedForkId,
        CuratedDraftOrigin origin,
        String candidateId,
        String sourceHostId,
        String runtimeId,
        CuratedDraftStatus status,
        List<Item> items,
        String createdBy,
        Instant createdAt
) implements BranchSelectionResult {
    public record Item(
            String id,
            int seq,
            CuratedDraftItemKind kind,
            CuratedDraftItemStatus status,
            String subjectId,
            String subjectName,
            String fromHostId,
            String fromHostName,
            String toHostId,
            String toHostName,
            boolean mergeKey,
            Map<String, Object> payload
    ) {
    }
}
