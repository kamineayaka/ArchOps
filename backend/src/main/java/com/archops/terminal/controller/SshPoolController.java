package com.archops.terminal.controller;

import com.archops.audit.service.AuditService;
import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.terminal.pool.SshConnectionPool;
import com.archops.terminal.pool.SshPoolEntryResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ssh/pool")
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
public class SshPoolController {

    private final SshConnectionPool sshConnectionPool;
    private final AuditService auditService;

    public SshPoolController(SshConnectionPool sshConnectionPool, AuditService auditService) {
        this.sshConnectionPool = sshConnectionPool;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<SshPoolEntryResponse>> list(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(
                sshConnectionPool.listForUser(principal.getUserId(), principal.roleNames()));
    }

    @PostMapping("/{assetId}/warm")
    public ApiResponse<Void> warm(
            @PathVariable Long assetId,
            @AuthenticationPrincipal AuthUserPrincipal principal) throws Exception {
        sshConnectionPool.warm(principal.getUserId(), principal.roleNames(), assetId);
        auditService.record(new AuditService.AuditEntry(
                principal.getUserId(),
                principal.getUsername(),
                "ssh.pool.warm",
                "asset:" + assetId,
                "LOW",
                "SUCCESS",
                null,
                null,
                null));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> evict(
            @PathVariable Long assetId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        sshConnectionPool.remove(principal.getUserId(), assetId);
        auditService.record(new AuditService.AuditEntry(
                principal.getUserId(),
                principal.getUsername(),
                "ssh.pool.evict",
                "asset:" + assetId,
                "LOW",
                "SUCCESS",
                null,
                null,
                null));
        return ApiResponse.ok(null);
    }
}
