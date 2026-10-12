package com.archops.observed.controller;

import com.archops.common.api.ApiResponse;
import com.archops.observed.dto.CreateProbeTaskRequest;
import com.archops.observed.dto.ProbeOverdueScanResponse;
import com.archops.observed.dto.ProbeTaskResponse;
import com.archops.observed.service.ProbeTaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control-plane probe tasks. Creating a task does not record an operator (ADR-0046 / ADR-0048).
 */
@RestController
@RequestMapping("/api/probe-tasks")
@PreAuthorize("isAuthenticated()")
public class ProbeTaskController {

    private final ProbeTaskService probeTaskService;

    public ProbeTaskController(ProbeTaskService probeTaskService) {
        this.probeTaskService = probeTaskService;
    }

    @PostMapping
    public ApiResponse<ProbeTaskResponse> create(@Valid @RequestBody CreateProbeTaskRequest request) {
        return ApiResponse.ok(probeTaskService.create(request));
    }

    @PostMapping("/scan-overdue")
    public ApiResponse<ProbeOverdueScanResponse> scanOverdue() {
        return ApiResponse.ok(probeTaskService.scanOverdue());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProbeTaskResponse> get(@PathVariable String id) {
        return ApiResponse.ok(probeTaskService.get(id));
    }
}
