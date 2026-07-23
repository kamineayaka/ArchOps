package com.archops.asset.dto;

public record TestConnectionResponse(
        boolean ok,
        long latencyMs,
        String message) {}
