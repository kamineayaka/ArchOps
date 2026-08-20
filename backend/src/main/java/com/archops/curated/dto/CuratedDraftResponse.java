package com.archops.curated.dto;

import com.archops.curated.domain.CuratedDraftItemKind;
import com.archops.curated.domain.CuratedDraftItemStatus;
import com.archops.curated.domain.CuratedDraftStatus;

import java.time.Instant;
import java.util.List;

public record CuratedDraftResponse(
        String id,
        String conflictId,
        String diagnosisId,
        String selectedForkId,
        CuratedDraftStatus status,
        List<Item> items,
        String createdBy,
        Instant createdAt
) {
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
            boolean mergeKey
    ) {
    }
}
