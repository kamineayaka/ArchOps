package com.archops.conflict.controller;

import com.archops.common.api.ApiResponse;
import com.archops.common.exception.BusinessException;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.dto.ConflictEventResponse;
import com.archops.conflict.dto.OpenOperationPlanResponse;
import com.archops.conflict.service.ConflictCaseAssembler;
import com.archops.conflict.service.ConflictCollaborationService;
import com.archops.conflict.service.ConflictEventService;
import com.archops.curated.domain.CuratedRelationType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conflict warn / collaboration / pending-close / diagnosis HTTP surface.
 * 确认关闭 is an explicit request and does not require a user identity.
 */
@RestController
@RequestMapping("/api/conflicts")
@PreAuthorize("isAuthenticated()")
public class ConflictController {

    private final ConflictCollaborationService conflictCollaborationService;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final ConflictEventService conflictEventService;
    private final ConflictCaseAssembler conflictCaseAssembler;

    public ConflictController(
            ConflictCollaborationService conflictCollaborationService,
            ConflictDiagnosisService conflictDiagnosisService,
            ConflictEventService conflictEventService,
            ConflictCaseAssembler conflictCaseAssembler
    ) {
        this.conflictCollaborationService = conflictCollaborationService;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.conflictEventService = conflictEventService;
        this.conflictCaseAssembler = conflictCaseAssembler;
    }

    /** Active conflicts: OPEN + PENDING_CLOSE (CLOSED excluded). */
    @GetMapping
    public ApiResponse<List<ConflictCaseResponse>> listActive() {
        return ApiResponse.ok(conflictCaseAssembler.listActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConflictCaseResponse> get(@PathVariable String id) {
        return ApiResponse.ok(conflictCaseAssembler.getById(id));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<ConflictEventResponse>> events(@PathVariable String id) {
        conflictCaseAssembler.getById(id);
        return ApiResponse.ok(conflictEventService.listForConflict(id));
    }

    @GetMapping("/{id}/diagnosis")
    public ApiResponse<ConflictDiagnosisResponse> diagnosis(@PathVariable String id) {
        conflictCaseAssembler.getById(id);
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
        return ApiResponse.ok(conflictCaseAssembler.getActiveByMergeKey(subjectId, relationType));
    }

    /** 认领不再写入已知悉、归属或处理人. */
    @PostMapping("/{id}/claim")
    public ApiResponse<ConflictCaseResponse> claim(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.claim(id));
    }

    /** 已知悉不再写入归属或处理人. */
    @PostMapping("/{id}/acknowledge")
    public ApiResponse<ConflictCaseResponse> acknowledge(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.acknowledge(id));
    }

    /** 自任不再写入已知悉或处理人. */
    @PostMapping("/{id}/acknowledge-and-self-appoint")
    public ApiResponse<ConflictCaseResponse> acknowledgeAndSelfAppoint(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.acknowledgeAndSelfAppoint(id));
    }

    /** 指派不再写入处理人，也不再要求指派对象. */
    @PostMapping("/{id}/assign-handler")
    public ApiResponse<ConflictCaseResponse> assignHandler(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.assignHandler(id));
    }

    /** 接受不再写入处理人. */
    @PostMapping("/{id}/accept-handler")
    public ApiResponse<ConflictCaseResponse> acceptHandler(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.acceptHandler(id));
    }

    /** 拒绝不再写入处理人，也不再要求理由. */
    @PostMapping("/{id}/reject-handler")
    public ApiResponse<ConflictCaseResponse> rejectHandler(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.rejectHandler(id));
    }

    /** 转让不再写入处理人，也不再要求接手人. */
    @PostMapping("/{id}/transfer-handler")
    public ApiResponse<ConflictCaseResponse> transferHandler(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.transferHandler(id));
    }

    /** 开计划不再以处理人门禁决定，响应不带处理人 id. */
    @PostMapping("/{id}/operation-plans")
    public ApiResponse<OpenOperationPlanResponse> openOperationPlan(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.openOperationPlan(id));
    }

    /**
     * 确认关闭：仅当策展=当前可用观测时成立；否则失败并提示刷新。不记录操作者。
     */
    @PostMapping("/{id}/confirm-close")
    @PreAuthorize("permitAll()")
    public ApiResponse<ConflictCaseResponse> confirmClose(@PathVariable String id) {
        return ApiResponse.ok(conflictCollaborationService.confirmClose(id));
    }
}
