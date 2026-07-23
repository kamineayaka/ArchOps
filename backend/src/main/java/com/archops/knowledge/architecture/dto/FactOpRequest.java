package com.archops.knowledge.architecture.dto;

import jakarta.validation.constraints.NotBlank;

/** Single fact operation for proposals / merge engine. */
public record FactOpRequest(
        @NotBlank String op,
        @NotBlank String factType,
        @NotBlank String subject,
        @NotBlank String predicate,
        @NotBlank String object,
        Long assetId,
        Double confidence,
        String provenance) {}
