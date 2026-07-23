package com.archops.knowledge.controller;

import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import com.archops.knowledge.domain.ArchitectureSnapshot;
import com.archops.knowledge.dto.ArchitectureRequest;
import com.archops.knowledge.dto.IndexStatsResponse;
import com.archops.knowledge.dto.ManualDocumentRequest;
import com.archops.knowledge.dto.ManualDocumentResponse;
import com.archops.knowledge.dto.RagChunkResponse;
import com.archops.knowledge.dto.RagSearchRequest;
import com.archops.knowledge.dto.ReindexResponse;
import com.archops.knowledge.dto.WorkLogResponse;
import com.archops.knowledge.service.KnowledgeRagService;
import com.archops.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeRagService knowledgeRagService;

    public KnowledgeController(KnowledgeService knowledgeService, KnowledgeRagService knowledgeRagService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeRagService = knowledgeRagService;
    }

    @GetMapping("/architecture")
    public ApiResponse<ArchitectureSnapshot> latest() {
        return ApiResponse.ok(knowledgeService.latestArchitecture());
    }

    @PostMapping("/architecture")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<ArchitectureSnapshot> update(
            @Valid @RequestBody ArchitectureRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(knowledgeService.updateArchitecture(
                request, principal.getUserId(), principal.getUsername()));
    }

    @GetMapping("/work-logs")
    public ApiResponse<List<WorkLogResponse>> workLogs(
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Long groupId) {
        return ApiResponse.ok(knowledgeService.findWorkLogs(conversationId, assetId, groupId));
    }

    @PostMapping("/search")
    public ApiResponse<List<RagChunkResponse>> search(@Valid @RequestBody RagSearchRequest request) {
        return ApiResponse.ok(knowledgeRagService.search(request.query(), request.topK()));
    }

    @GetMapping("/index-stats")
    public ApiResponse<IndexStatsResponse> indexStats() {
        return ApiResponse.ok(knowledgeRagService.stats());
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<ReindexResponse> reindex() {
        return ApiResponse.ok(knowledgeRagService.reindexAll());
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<ManualDocumentResponse> indexDocument(@Valid @RequestBody ManualDocumentRequest request) {
        return ApiResponse.ok(knowledgeRagService.indexManualDocument(request.title(), request.content()));
    }
}
