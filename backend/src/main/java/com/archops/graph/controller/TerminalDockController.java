package com.archops.graph.controller;

import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.graph.dto.TerminalDockItem;
import com.archops.graph.dto.TerminalDockUpsertRequest;
import com.archops.graph.service.TerminalDockService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/terminal/dock")
public class TerminalDockController {

    private final TerminalDockService terminalDockService;

    public TerminalDockController(TerminalDockService terminalDockService) {
        this.terminalDockService = terminalDockService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<TerminalDockItem>> list(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(terminalDockService.list(principal.getUserId(), principal.roleNames()));
    }

    @PostMapping("/touch")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<TerminalDockItem> touch(
            @Valid @RequestBody TerminalDockUpsertRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(
                terminalDockService.touch(principal.getUserId(), principal.roleNames(), request));
    }

    @PutMapping("/{elementId}/pin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<TerminalDockItem> pin(
            @PathVariable UUID elementId,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        boolean pinned = body != null && Boolean.TRUE.equals(body.get("pinned"));
        return ApiResponse.ok(terminalDockService.setPinned(
                principal.getUserId(), principal.roleNames(), elementId, pinned));
    }

    @DeleteMapping("/{elementId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<Void> remove(
            @PathVariable UUID elementId, @AuthenticationPrincipal AuthUserPrincipal principal) {
        terminalDockService.remove(principal.getUserId(), principal.roleNames(), elementId);
        return ApiResponse.ok("ok", null);
    }
}
