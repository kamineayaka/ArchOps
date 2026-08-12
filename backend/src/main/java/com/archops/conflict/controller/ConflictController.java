package com.archops.conflict.controller;

import com.archops.common.api.ApiResponse;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.service.ConflictDetectionService;
import com.archops.curated.domain.CuratedRelationType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conflict warn / upgrade query surface. Diagnosis is not required to read warnings.
 */
@RestController
@RequestMapping("/api/conflicts")
@PreAuthorize("isAuthenticated()")
public class ConflictController {

    private final ConflictDetectionService conflictDetectionService;

    public ConflictController(ConflictDetectionService conflictDetectionService) {
        this.conflictDetectionService = conflictDetectionService;
    }

    @GetMapping
    public ApiResponse<List<ConflictCaseResponse>> listOpen() {
        return ApiResponse.ok(conflictDetectionService.listOpen());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConflictCaseResponse> get(@PathVariable String id) {
        return ApiResponse.ok(conflictDetectionService.getById(id));
    }

    @GetMapping("/by-merge-key")
    public ApiResponse<ConflictCaseResponse> byMergeKey(
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "RUNS_ON") CuratedRelationType relationType
    ) {
        return ApiResponse.ok(conflictDetectionService.getOpenByMergeKey(subjectId, relationType));
    }
}
