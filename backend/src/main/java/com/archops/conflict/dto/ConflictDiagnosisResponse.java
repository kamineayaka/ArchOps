package com.archops.conflict.dto;

import com.archops.conflict.domain.DiagnosisSource;
import com.archops.conflict.domain.DiagnosisStatus;

import java.time.Instant;
import java.util.List;

public record ConflictDiagnosisResponse(
        String id,
        String conflictId,
        DiagnosisStatus status,
        DiagnosisSource source,
        String summary,
        List<ForkSuggestion> forks,
        Instant createdAt,
        Instant completedAt,
        String errorMessage
) {
    public record ForkSuggestion(
            String id,
            String label,
            String kind,
            String hypothesis,
            String description
    ) {
    }
}
