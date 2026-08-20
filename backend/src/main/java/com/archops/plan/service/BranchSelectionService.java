package com.archops.plan.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.diagnosis.DiagnosisRuleEngine;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.DiagnosisStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.service.CuratedDraftService;
import com.archops.user.security.AuthUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single select-branch gate: 已接受处理人 + 当前未过时诊断 + 每冲突一条活跃处理路径.
 * FIX_ACTUAL still creates an 操作计划; CHANGE_CURATED creates a 草案 and no plan.
 */
@Service
public class BranchSelectionService {

    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final OperationPlanService operationPlanService;
    private final CuratedDraftService curatedDraftService;

    public BranchSelectionService(
            ConflictCaseMapper conflictCaseMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            OperationPlanService operationPlanService,
            CuratedDraftService curatedDraftService
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.operationPlanService = operationPlanService;
        this.curatedDraftService = curatedDraftService;
    }

    @Transactional
    public Object select(String conflictId, String forkId, String expectedDiagnosisId, AuthUserPrincipal actor) {
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

        if (isChangeCurated(fork)) {
            if (operationPlanService.hasActive(conflictId)) {
                throw new BusinessException("PLAN_ALREADY_ACTIVE",
                        "Conflict already has an active operation plan");
            }
            return curatedDraftService.createForChangeCurated(conflict, diagnosis, fork, actor);
        }
        if (isFixActual(fork)) {
            if (curatedDraftService.hasOpen(conflictId)) {
                throw new BusinessException("OPEN_DRAFT_BLOCKS_FIX_ACTUAL",
                        "Open 改理想草案 blocks 修实际 branch selection");
            }
            return operationPlanService.selectBranch(conflictId, forkId, actor);
        }
        throw new BusinessException("FORK_NOT_SUPPORTED",
                "Unsupported diagnosis fork for branch selection: " + fork.id());
    }

    private static boolean isChangeCurated(ConflictDiagnosisResponse.ForkSuggestion fork) {
        return DiagnosisRuleEngine.CHANGE_CURATED_TO_OBSERVED.equals(fork.id())
                || "CHANGE_CURATED".equals(fork.kind());
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
