package com.archops.curated.controller;

import com.archops.common.api.ApiResponse;
import com.archops.curated.dto.CuratedDraftResponse;
import com.archops.curated.service.CuratedDraftService;
import com.archops.user.security.AuthUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open 改理想 草案 reads (ticket 03) and itemized accept/reject (ticket 04).
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

    @PostMapping("/curated-drafts/{draftId}/items/{itemId}/accept")
    public ApiResponse<CuratedDraftResponse> acceptItem(
            @PathVariable String draftId,
            @PathVariable String itemId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedDraftService.acceptItem(draftId, itemId, principal));
    }

    @PostMapping("/curated-drafts/{draftId}/items/{itemId}/reject")
    public ApiResponse<CuratedDraftResponse> rejectItem(
            @PathVariable String draftId,
            @PathVariable String itemId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedDraftService.rejectItem(draftId, itemId, principal));
    }
}
