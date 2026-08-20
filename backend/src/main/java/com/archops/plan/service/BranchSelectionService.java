package com.archops.plan.service;

import com.archops.common.api.BranchSelectionResult;
import com.archops.common.exception.BusinessException;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.diagnosis.DiagnosisRuleEngine;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.DiagnosisStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.user.security.AuthUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single select-branch gate: 已接受处理人 + 当前未过时诊断 + 每冲突一条活跃处理路径.
 * Ticket 03 TDD redo starts from the vertical-slice gate: only FIX_ACTUAL creates an 操作计划.
 */
@Service
public class BranchSelectionService {

    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final OperationPlanService operationPlanService;

    public BranchSelectionService(
            ConflictCaseMapper conflictCaseMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            OperationPlanService operationPlanService
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.operationPlanService = operationPlanService;
    }

    @Transactional
    public BranchSelectionResult select(String conflictId, String forkId, String expectedDiagnosisId, AuthUserPrincipal actor) {
        ConflictCase conflict = requireOpenConflict(conflictId);
        requireAcceptedHandler(conflict, actor);

        ConflictDiagnosisResponse diagnosis = conflictDiagnosisService.latestForConflict(conflictId);
        if (diagnosis == null || diagnosis.status() != DiagnosisStatus.READY) {
            throw new BusinessException("DIAGNOSIS_NOT_READY",
                    "Branch selection requires a READY diagnosis");
        }
        if (expectedDiagnosisId != null && !expectedDiagnosisId.isBlank()
                && !expectedDiagnosisId.equals(diagnosis.id())) {
            throw new BusinessException("DIAGNOSIS_NOT_READY",
                    "Branch selection requires the current non-stale READY diagnosis");
        }

        ConflictDiagnosisResponse.ForkSuggestion fork = diagnosis.forks().stream()
                .filter(f -> forkId.equals(f.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("FORK_NOT_FOUND",
                        "Fork not present on current diagnosis: " + forkId));

        if (isFixActual(fork)) {
            return operationPlanService.selectBranch(conflictId, forkId, actor);
        }
        throw new BusinessException("FORK_NOT_SUPPORTED",
                "Ticket 07 only supports FIX_ACTUAL / 修实际回策展宿主");
    }

    private static boolean isFixActual(ConflictDiagnosisResponse.ForkSuggestion fork) {
        return DiagnosisRuleEngine.FIX_ACTUAL_TO_CURATED.equals(fork.id())
                || "FIX_ACTUAL".equals(fork.kind());
    }

    private ConflictCase requireOpenConflict(String conflictId) {
        ConflictCase row = conflictCaseMapper.selectById(conflictId);
        if (row == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + conflictId);
        }
        if (row.getStatus() != ConflictStatus.OPEN) {
            throw new BusinessException("CONFLICT_NOT_OPEN", "Conflict is not open: " + conflictId);
        }
        return row;
    }

    private static void requireAcceptedHandler(ConflictCase conflict, AuthUserPrincipal actor) {
        boolean ok = conflict.getHandlerAcceptance() == HandlerAcceptance.ACCEPTED
                && actor.getUserId().equals(conflict.getHandlerUserId());
        if (!ok) {
            throw new BusinessException("PLAN_REQUIRES_ACCEPTED_HANDLER",
                    "Only the 已接受冲突处理人 may select a branch or manage the operation plan");
        }
    }
}
