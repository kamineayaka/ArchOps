package com.archops.asset.dbquery;

import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class DbQueryController {

    private final DbQueryService dbQueryService;

    public DbQueryController(DbQueryService dbQueryService) {
        this.dbQueryService = dbQueryService;
    }

    @PostMapping("/{id}/query")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<DbQueryResponse> query(
            @PathVariable Long id,
            @Valid @RequestBody DbQueryRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(
                dbQueryService.submit(principal.getUserId(), id, request.sql(), request.approvalId()));
    }
}
