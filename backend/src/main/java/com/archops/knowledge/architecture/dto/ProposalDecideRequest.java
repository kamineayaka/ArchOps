package com.archops.knowledge.architecture.dto;

import jakarta.validation.constraints.NotBlank;

public record ProposalDecideRequest(@NotBlank String decision, String comment) {}
