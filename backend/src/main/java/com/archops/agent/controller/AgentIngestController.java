package com.archops.agent.controller;

import com.archops.agent.dto.AgentHeartbeatRequest;
import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.common.api.ApiResponse;
import com.archops.observed.dto.AgentFreshnessResponse;
import com.archops.observed.service.ObservedTruthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Host-agent ingest surface (heartbeat + optional snapshot).
 * Public control-plane API — no operator identity header required.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentIngestController {

    private final ObservedTruthService observedTruthService;

    public AgentIngestController(ObservedTruthService observedTruthService) {
        this.observedTruthService = observedTruthService;
    }

    @PostMapping("/heartbeat")
    public ApiResponse<AgentHeartbeatResponse> heartbeat(@Valid @RequestBody AgentHeartbeatRequest request) {
        return ApiResponse.ok(observedTruthService.ingestHeartbeat(request));
    }

    @GetMapping("/{agentId}/freshness")
    public ApiResponse<AgentFreshnessResponse> freshness(@PathVariable String agentId) {
        return ApiResponse.ok(observedTruthService.freshness(agentId));
    }
}
