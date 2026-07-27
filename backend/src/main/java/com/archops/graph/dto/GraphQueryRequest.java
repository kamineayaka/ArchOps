package com.archops.graph.dto;

import jakarta.validation.constraints.NotBlank;

public record GraphQueryRequest(@NotBlank String cypher) {}
