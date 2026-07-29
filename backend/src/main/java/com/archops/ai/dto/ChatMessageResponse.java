package com.archops.ai.dto;

import java.time.Instant;

public record ChatMessageResponse(
        String role,
        String content,
        Instant createdAt,
        /** Raw JSON from {@code ai_messages.tool_calls} (tool summaries or pending tool calls). */
        String toolCalls) {}
