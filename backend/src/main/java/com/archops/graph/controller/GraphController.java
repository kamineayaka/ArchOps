package com.archops.graph.controller;

import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.graph.dto.CredentialStagingCreateRequest;
import com.archops.graph.dto.CredentialStagingResponse;
import com.archops.graph.dto.GraphPlanRequest;
import com.archops.graph.dto.GraphPlanResponse;
import com.archops.graph.dto.GraphQueryRequest;
import com.archops.graph.dto.GraphQueryResponse;
import com.archops.graph.dto.GraphSnapshotResponse;
import com.archops.graph.service.CredentialStagingService;
import com.archops.graph.service.GraphPlanService;
import com.archops.graph.service.GraphReadService;
import com.archops.graph.service.GraphVersionService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphReadService graphReadService;
    private final GraphPlanService graphPlanService;
    private final GraphVersionService graphVersionService;
    private final CredentialStagingService credentialStagingService;

    public GraphController(
            GraphReadService graphReadService,
            GraphPlanService graphPlanService,
            GraphVersionService graphVersionService,
            CredentialStagingService credentialStagingService) {
        this.graphReadService = graphReadService;
        this.graphPlanService = graphPlanService;
        this.graphVersionService = graphVersionService;
        this.credentialStagingService = credentialStagingService;
    }

    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<Map<String, Object>> meta() {
        return ApiResponse.ok(Map.of(
                "graphVersion", graphVersionService.currentVersion(),
                "partitionKey", "graph:global"));
    }

    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<GraphSnapshotResponse> snapshot() {
        return ApiResponse.ok(graphReadService.snapshot());
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR') or hasAuthority('ROLE_VIEWER')")
    public ApiResponse<GraphQueryResponse> query(@Valid @RequestBody GraphQueryRequest request) {
        return ApiResponse.ok(graphReadService.query(request.cypher()));
    }

    @PostMapping("/plan")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<GraphPlanResponse> plan(
            @Valid @RequestBody GraphPlanRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(graphPlanService.plan(request));
    }

    @PostMapping("/credential-staging")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<CredentialStagingResponse> stageCredential(
            @Valid @RequestBody CredentialStagingCreateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(credentialStagingService.create(request, principal.getUserId()));
    }
}
