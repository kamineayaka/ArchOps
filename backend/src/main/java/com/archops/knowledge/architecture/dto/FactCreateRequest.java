package com.archops.knowledge.architecture.dto;

import jakarta.validation.constraints.NotBlank;

public record FactCreateRequest(
        @NotBlank String factType,
        @NotBlank String subject,
        @NotBlank String predicate,
        @NotBlank String object,
        Long assetId,
        Double confidence,
        String provenanceJson) {}
