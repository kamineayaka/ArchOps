package com.archops.curated.controller;

import com.archops.common.api.ApiResponse;
import com.archops.curated.dto.CuratedDraftResponse;
import com.archops.curated.service.CuratedDraftService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open 改理想 草案 reads (ticket 03). Writes stay on the shared branch-selection POST.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class CuratedDraftController {

    private final CuratedDraftService curatedDraftService;

    public CuratedDraftController(CuratedDraftService curatedDraftService) {
        this.curatedDraftService = curatedDraftService;
    }

    @GetMapping("/conflicts/{conflictId}/curated-drafts/open")
    public ApiResponse<CuratedDraftResponse> openDraft(@PathVariable String conflictId) {
        return ApiResponse.ok(curatedDraftService.getOpen(conflictId));
    }
}
