package com.archops.observed.controller;

import com.archops.common.api.ApiResponse;
import com.archops.observed.dto.ActualWhereResponse;
import com.archops.observed.dto.IdentityLostResponse;
import com.archops.observed.dto.UnboundCandidateResponse;
import com.archops.observed.service.ObservedTruthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Observed-truth reads for 规范问法 and unbound / identity-lost inspection.
 */
@RestController
@RequestMapping("/api/observed")
@PreAuthorize("isAuthenticated()")
public class ObservedController {

    private final ObservedTruthService observedTruthService;

    public ObservedController(ObservedTruthService observedTruthService) {
        this.observedTruthService = observedTruthService;
    }

    /**
     * 规范问法：「实际在哪」— observed answer with curated value co-displayed (P2).
     */
    @GetMapping("/asks/actual-where")
    public ApiResponse<ActualWhereResponse> actualWhere(@RequestParam String containerId) {
        return ApiResponse.ok(observedTruthService.actualWhere(containerId));
    }

    @GetMapping("/unbound-candidates")
    public ApiResponse<List<UnboundCandidateResponse>> unboundCandidates() {
        return ApiResponse.ok(observedTruthService.listUnbound());
    }

    @GetMapping("/identity-lost/{curatedObjectId}")
    public ApiResponse<IdentityLostResponse> identityLost(@PathVariable String curatedObjectId) {
        return ApiResponse.ok(observedTruthService.getIdentityLost(curatedObjectId));
    }
}
