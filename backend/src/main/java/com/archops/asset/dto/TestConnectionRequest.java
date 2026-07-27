package com.archops.asset.dto;

import jakarta.validation.constraints.NotNull;

/** Test connectivity for a saved asset (credentials + graph connect path). */
public record TestConnectionRequest(@NotNull Long assetId) {}
