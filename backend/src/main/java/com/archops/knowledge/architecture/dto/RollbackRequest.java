package com.archops.knowledge.architecture.dto;

import jakarta.validation.constraints.NotNull;

public record RollbackRequest(@NotNull Long targetVersion) {}
