package com.archops.conflict.dto;

import jakarta.validation.constraints.NotBlank;

/** Pending handler rejects assignment; reason is required by contract. */
public record RejectHandlerRequest(
        @NotBlank String reason
) {
}
