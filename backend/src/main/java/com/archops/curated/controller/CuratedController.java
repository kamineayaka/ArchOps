package com.archops.curated.controller;

import com.archops.common.api.ApiResponse;
import com.archops.curated.dto.ConfirmRunsOnRequest;
import com.archops.curated.dto.CreateContainerRequest;
import com.archops.curated.dto.CreateHostRequest;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.dto.CuratedRunsOnFactResponse;
import com.archops.curated.dto.HostSshCredentialResponse;
import com.archops.curated.dto.ShouldWhereResponse;
import com.archops.curated.dto.UpsertHostSshCredentialRequest;
import com.archops.curated.service.CuratedTruthService;
import com.archops.curated.service.HostSshCredentialService;
import com.archops.user.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Curated-truth HTTP surface for physical hosts, Docker containers, and 运行于 facts.
 */
@RestController
@RequestMapping("/api/curated")
@PreAuthorize("isAuthenticated()")
public class CuratedController {

    private final CuratedTruthService curatedTruthService;
    private final HostSshCredentialService hostSshCredentialService;

    public CuratedController(
            CuratedTruthService curatedTruthService,
            HostSshCredentialService hostSshCredentialService
    ) {
        this.curatedTruthService = curatedTruthService;
        this.hostSshCredentialService = hostSshCredentialService;
    }

    @PostMapping("/hosts")
    public ApiResponse<CuratedObjectResponse> createHost(
            @Valid @RequestBody CreateHostRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedTruthService.createHost(request, principal.getUserId()));
    }

    @PostMapping("/containers")
    public ApiResponse<CuratedObjectResponse> createContainer(
            @Valid @RequestBody CreateContainerRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedTruthService.createContainer(request, principal.getUserId()));
    }

    @PostMapping("/facts/runs-on")
    public ApiResponse<CuratedRunsOnFactResponse> confirmRunsOn(
            @Valid @RequestBody ConfirmRunsOnRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedTruthService.confirmRunsOn(request, principal.getUserId()));
    }

    @GetMapping("/facts/runs-on/{containerId}")
    public ApiResponse<CuratedRunsOnFactResponse> getRunsOn(@PathVariable String containerId) {
        return ApiResponse.ok(curatedTruthService.getRunsOn(containerId));
    }

    /**
     * 规范问法：「应该在哪」— curated track only (ideal host for the container).
     */
    @GetMapping("/asks/should-where")
    public ApiResponse<ShouldWhereResponse> shouldWhere(@RequestParam String containerId) {
        return ApiResponse.ok(curatedTruthService.shouldWhere(containerId));
    }

    /**
     * Store encrypted SSH credentials for a graph-resident physical host (ticket 08).
     * Request secret is never returned.
     */
    @PutMapping("/hosts/{hostId}/ssh-credential")
    public ApiResponse<HostSshCredentialResponse> upsertSshCredential(
            @PathVariable String hostId,
            @Valid @RequestBody UpsertHostSshCredentialRequest request
    ) {
        return ApiResponse.ok(hostSshCredentialService.upsert(hostId, request));
    }

    @GetMapping("/hosts/{hostId}/ssh-credential")
    public ApiResponse<HostSshCredentialResponse> getSshCredential(@PathVariable String hostId) {
        return ApiResponse.ok(hostSshCredentialService.get(hostId));
    }
}
