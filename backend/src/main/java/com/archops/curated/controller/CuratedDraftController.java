package com.archops.curated.controller;

import com.archops.common.api.ApiResponse;
import com.archops.curated.dto.CuratedDraftEventResponse;
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

import java.util.List;

/**
 * 改理想草案 reads / item review, plus 未绑定草案 by-id reads and per-item accept/reject.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class CuratedDraftController {

    private final CuratedDraftService curatedDraftService;

    public CuratedDraftController(CuratedDraftService curatedDraftService) {
        this.curatedDraftService = curatedDraftService;
    }

    @GetMapping("/curated-drafts/{draftId}")
    public ApiResponse<CuratedDraftResponse> draftByGlobalId(@PathVariable String draftId) {
        return ApiResponse.ok(curatedDraftService.getByDraftId(draftId));
    }

    @GetMapping("/curated-drafts/{draftId}/events")
    public ApiResponse<List<CuratedDraftEventResponse>> draftEvents(@PathVariable String draftId) {
        return ApiResponse.ok(curatedDraftService.listEvents(draftId));
    }

    @GetMapping("/conflicts/{conflictId}/curated-drafts/open")
    public ApiResponse<CuratedDraftResponse> openDraft(@PathVariable String conflictId) {
        return ApiResponse.ok(curatedDraftService.getOpen(conflictId));
    }

    @GetMapping("/conflicts/{conflictId}/curated-drafts/{draftId}")
    public ApiResponse<CuratedDraftResponse> draftById(
            @PathVariable String conflictId,
            @PathVariable String draftId
    ) {
        return ApiResponse.ok(curatedDraftService.getById(conflictId, draftId));
    }

    @PostMapping("/curated-drafts/{draftId}/items/{itemId}/accept")
    public ApiResponse<CuratedDraftResponse> acceptUnboundItem(
            @PathVariable String draftId,
            @PathVariable String itemId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedDraftService.acceptUnboundItem(draftId, itemId, principal));
    }

    @PostMapping("/curated-drafts/{draftId}/items/{itemId}/reject")
    public ApiResponse<CuratedDraftResponse> rejectUnboundItem(
            @PathVariable String draftId,
            @PathVariable String itemId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedDraftService.rejectUnboundItem(draftId, itemId, principal));
    }

    @PostMapping("/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/accept")
    public ApiResponse<CuratedDraftResponse> acceptItem(
            @PathVariable String conflictId,
            @PathVariable String itemId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedDraftService.acceptItem(conflictId, itemId, principal));
    }

    @PostMapping("/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/reject")
    public ApiResponse<CuratedDraftResponse> rejectItem(
            @PathVariable String conflictId,
            @PathVariable String itemId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(curatedDraftService.rejectItem(conflictId, itemId, principal));
    }
}
