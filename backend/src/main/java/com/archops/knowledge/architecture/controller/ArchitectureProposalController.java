package com.archops.knowledge.architecture.controller;

import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.dto.ProposalCreateRequest;
import com.archops.knowledge.architecture.dto.ProposalDecideRequest;
import com.archops.knowledge.architecture.dto.ProposalResponse;
import com.archops.knowledge.architecture.service.ArchitectureProposalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/architecture/proposals")
public class ArchitectureProposalController {

    private final ArchitectureProposalService proposalService;

    public ArchitectureProposalController(ArchitectureProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<List<ProposalResponse>> list(
            @RequestParam(required = false) ProposalStatus status,
            @RequestParam(required = false) String partitionKey) {
        return ApiResponse.ok(proposalService.list(status, partitionKey));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<ProposalResponse> create(
            @Valid @RequestBody ProposalCreateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(proposalService.create(request, principal.getUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<ProposalResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(proposalService.get(id));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<ProposalResponse> decide(
            @PathVariable Long id,
            @Valid @RequestBody ProposalDecideRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(proposalService.decide(
                id, request, principal.getUserId(), principal.getAuthorities()));
    }
}
