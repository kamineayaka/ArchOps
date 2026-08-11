package com.archops.user.controller;

import com.archops.common.api.ApiResponse;
import com.archops.user.dto.CurrentUserResponse;
import com.archops.user.security.AuthUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(CurrentUserResponse.from(
                principal.getUserId(),
                principal.getDisplayName(),
                principal.getRole()
        ));
    }

    /**
     * Role gate probe: only 高级角色 (SENIOR) may pass.
     * Used to prove role differentiation before collaboration rules land.
     */
    @GetMapping("/probes/senior")
    @PreAuthorize("hasRole('SENIOR')")
    public ApiResponse<Map<String, Object>> seniorProbe(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(Map.of(
                "allowed", true,
                "userId", principal.getUserId(),
                "role", principal.getRole().name()
        ));
    }

    /**
     * Authenticated-any-role probe: both SENIOR and GENERAL may pass.
     */
    @GetMapping("/probes/authenticated")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> authenticatedProbe(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(Map.of(
                "allowed", true,
                "userId", principal.getUserId(),
                "role", principal.getRole().name()
        ));
    }
}
