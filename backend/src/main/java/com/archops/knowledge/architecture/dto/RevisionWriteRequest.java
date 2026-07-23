package com.archops.knowledge.architecture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RevisionWriteRequest(
        String summary,
        String bodyMd,
        String structuredJson,
        @NotNull Long baseVersion,
        @Valid List<FactCreateRequest> facts) {}
