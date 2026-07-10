package com.cloudops.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, Long conversationId, Long providerId) {}
