package com.archops.conflict.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Current handler (待接受 or 已接受) transfers handler role to another 一般角色.
 * Ownership is unchanged; recipient must accept (PENDING_ACCEPT).
 */
public record TransferHandlerRequest(
        @NotBlank String toUserId
) {
}
