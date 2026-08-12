package com.archops.conflict.controller;

import com.archops.common.api.ApiResponse;
import com.archops.common.exception.BusinessException;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.dto.AssignHandlerRequest;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.dto.ConflictEventResponse;
import com.archops.conflict.dto.OpenOperationPlanResponse;
import com.archops.conflict.dto.RejectHandlerRequest;
import com.archops.conflict.dto.TransferHandlerRequest;
import com.archops.conflict.service.ConflictCollaborationService;
import com.archops.conflict.service.ConflictDetectionService;
import com.archops.conflict.service.ConflictEventService;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.user.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conflict warn / collaboration / pending-close / diagnosis HTTP surface.
 */
@RestController
@RequestMapping("/api/conflicts")
@PreAuthorize("isAuthenticated()")
public class ConflictController {

    private final ConflictDetectionService conflictDetectionService;
    private final ConflictCollaborationService conflictCollaborationService;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final ConflictEventService conflictEventService;

    public ConflictController(
            ConflictDetectionService conflictDetectionService,
            ConflictCollaborationService conflictCollaborationService,
            ConflictDiagnosisService conflictDiagnosisService,
            ConflictEventService conflictEventService
    ) {
        this.conflictDetectionService = conflictDetectionService;
        this.conflictCollaborationService = conflictCollaborationService;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.conflictEventService = conflictEventService;
    }

    /** Active conflicts: OPEN + PENDING_CLOSE (CLOSED excluded). */
    @GetMapping
    public ApiResponse<List<ConflictCaseResponse>> listActive() {
        return ApiResponse.ok(conflictDetectionService.listActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConflictCaseResponse> get(@PathVariable String id) {
        return ApiResponse.ok(conflictDetectionService.getById(id));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<ConflictEventResponse>> events(@PathVariable String id) {
        conflictDetectionService.getById(id);
        return ApiResponse.ok(conflictEventService.listForConflict(id));
    }

    @GetMapping("/{id}/diagnosis")
    public ApiResponse<ConflictDiagnosisResponse> diagnosis(@PathVariable String id) {
        conflictDetectionService.getById(id);
        ConflictDiagnosisResponse latest = conflictDiagnosisService.latestForConflict(id);
        if (latest == null) {
            throw new BusinessException("DIAGNOSIS_NOT_FOUND", "No diagnosis for conflict: " + id);
        }
        return ApiResponse.ok(latest);
    }

    @GetMapping("/by-merge-key")
    public ApiResponse<ConflictCaseResponse> byMergeKey(
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "RUNS_ON") CuratedRelationType relationType
    ) {
        return ApiResponse.ok(conflictDetectionService.getActiveByMergeKey(subjectId, relationType));
    }

    /** 一般角色认领未已知悉冲突 → 已接受处理人（含归属）. */
    @PostMapping("/{id}/claim")
    public ApiResponse<ConflictCaseResponse> claim(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.claim(id, principal));
    }

    /** 高级角色已知悉 → 冲突归属（可不设处理人）. */
    @PostMapping("/{id}/acknowledge")
    public ApiResponse<ConflictCaseResponse> acknowledge(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.acknowledge(id, principal));
    }

    /** 高级角色已知悉并自任为已接受处理人. */
    @PostMapping("/{id}/acknowledge-and-self-appoint")
    public ApiResponse<ConflictCaseResponse> acknowledgeAndSelfAppoint(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.acknowledgeAndSelfAppoint(id, principal));
    }

    /** 归属方（高级角色）指派一般角色为待接受处理人. */
    @PostMapping("/{id}/assign-handler")
    public ApiResponse<ConflictCaseResponse> assignHandler(
            @PathVariable String id,
            @Valid @RequestBody AssignHandlerRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.assignHandler(id, request.assigneeUserId(), principal));
    }

    /** 待接受处理人接受指派/转让. */
    @PostMapping("/{id}/accept-handler")
    public ApiResponse<ConflictCaseResponse> acceptHandler(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.acceptHandler(id, principal));
    }

    /** 待接受处理人拒绝（须理由）. */
    @PostMapping("/{id}/reject-handler")
    public ApiResponse<ConflictCaseResponse> rejectHandler(
            @PathVariable String id,
            @Valid @RequestBody RejectHandlerRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.rejectHandler(id, request.reason(), principal));
    }

    /** 当前处理人转让给另一一般角色（拟接手人待接受；归属不变）. */
    @PostMapping("/{id}/transfer-handler")
    public ApiResponse<ConflictCaseResponse> transferHandler(
            @PathVariable String id,
            @Valid @RequestBody TransferHandlerRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.transferHandler(id, request.toUserId(), principal));
    }

    /**
     * Attempt to open an operation plan for this conflict (gate only; plan body is ticket 07).
     */
    @PostMapping("/{id}/operation-plans")
    public ApiResponse<OpenOperationPlanResponse> openOperationPlan(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.openOperationPlan(id, principal));
    }

    /**
     * 已接受处理人确认关闭：仅当策展=当前可用观测时成立；否则失败并提示刷新。
     */
    @PostMapping("/{id}/confirm-close")
    public ApiResponse<ConflictCaseResponse> confirmClose(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ApiResponse.ok(conflictCollaborationService.confirmClose(id, principal));
    }
}
