package com.archops.asset.dbquery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DbQueryRequest(
        @NotBlank(message = "SQL 不能为空") @Size(max = 100_000) String sql,
        Long approvalId) {}
