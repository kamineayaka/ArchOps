package com.archops.conflict.dto;

import jakarta.validation.constraints.NotBlank;

/** Senior/owner assigns a 一般角色 as 待接受冲突处理人. */
public record AssignHandlerRequest(
        @NotBlank String assigneeUserId
) {
}
