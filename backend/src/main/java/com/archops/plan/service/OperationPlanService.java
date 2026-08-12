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
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.plan.domain.OperationPlan;
import com.archops.plan.domain.OperationPlanStatus;
import com.archops.plan.domain.PlanBranchKind;
import com.archops.plan.dto.OperationPlanResponse;
import com.archops.plan.dto.StartExecutionResponse;
import com.archops.plan.mapper.OperationPlanMapper;
import com.archops.user.security.AuthUserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Branch selection → operation plan generation → human review (ticket 07).
 * SSH execution is ticket 08.
 */
@Service
public class OperationPlanService {

    private static final List<OperationPlanStatus> ACTIVE = List.of(
            OperationPlanStatus.DRAFT_REVIEW,
            OperationPlanStatus.APPROVED,
            OperationPlanStatus.EXECUTING
    );

    private final OperationPlanMapper operationPlanMapper;
    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final CuratedObjectMapper curatedObjectMapper;
    private final ObjectMapper objectMapper;

    public OperationPlanService(
            OperationPlanMapper operationPlanMapper,
            ConflictCaseMapper conflictCaseMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            CuratedObjectMapper curatedObjectMapper,
            ObjectMapper objectMapper
    ) {
        this.operationPlanMapper = operationPlanMapper;
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.curatedObjectMapper = curatedObjectMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OperationPlanResponse selectBranch(String conflictId, String forkId, AuthUserPrincipal actor) {
        ConflictCase conflict = requireOpenConflict(conflictId);
        requireAcceptedHandler(conflict, actor);

        if (findActive(conflictId) != null) {
            throw new BusinessException("PLAN_ALREADY_ACTIVE",
                    "Conflict already has an active operation plan");
        }

        ConflictDiagnosisResponse diagnosis = conflictDiagnosisService.latestForConflict(conflictId);
        if (diagnosis == null || diagnosis.status() != DiagnosisStatus.READY) {
            throw new BusinessException("DIAGNOSIS_NOT_READY",
                    "Branch selection requires a READY diagnosis");
        }
        ConflictDiagnosisResponse.ForkSuggestion fork = diagnosis.forks().stream()
                .filter(f -> forkId.equals(f.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("FORK_NOT_FOUND",
                        "Fork not present on current diagnosis: " + forkId));

        if (!DiagnosisRuleEngine.FIX_ACTUAL_TO_CURATED.equals(fork.id())
                && !"FIX_ACTUAL".equals(fork.kind())) {
            throw new BusinessException("FORK_NOT_SUPPORTED",
                    "Ticket 07 only supports FIX_ACTUAL / 修实际回策展宿主");
        }

        Instant now = Instant.now();
        List<OperationPlanResponse.PlanStep> steps = buildFixActualSteps(conflict);
        OperationPlan plan = new OperationPlan();
        plan.setId("plan-" + UUID.randomUUID());
        plan.setConflictId(conflictId);
        plan.setDiagnosisId(diagnosis.id());
        plan.setSelectedForkId(fork.id());
        plan.setBranchKind(PlanBranchKind.FIX_ACTUAL);
        plan.setSkipsDraft(true);
        plan.setStatus(OperationPlanStatus.DRAFT_REVIEW);
        plan.setStepsJson(writeSteps(steps));
        plan.setCreatedBy(actor.getUserId());
        plan.setCreatedAt(now);
        operationPlanMapper.insert(plan);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public OperationPlanResponse getActive(String conflictId) {
        requireOpenConflict(conflictId);
        OperationPlan active = findActive(conflictId);
        if (active == null) {
            throw new BusinessException("PLAN_NOT_FOUND", "No active operation plan for conflict: " + conflictId);
        }
        return toResponse(active);
    }

    @Transactional(readOnly = true)
    public OperationPlanResponse getById(String planId) {
        OperationPlan plan = operationPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("PLAN_NOT_FOUND", "Operation plan not found: " + planId);
        }
        return toResponse(plan);
    }

    @Transactional
    public OperationPlanResponse approve(String planId, AuthUserPrincipal actor) {
        OperationPlan plan = requirePlan(planId);
        ConflictCase conflict = requireOpenConflict(plan.getConflictId());
        requireAcceptedHandler(conflict, actor);
        if (plan.getStatus() != OperationPlanStatus.DRAFT_REVIEW) {
            throw new BusinessException("PLAN_NOT_IN_REVIEW",
                    "Only DRAFT_REVIEW plans can be approved");
        }
        Instant now = Instant.now();
        operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                .eq(OperationPlan::getId, planId)
                .eq(OperationPlan::getStatus, OperationPlanStatus.DRAFT_REVIEW)
                .set(OperationPlan::getStatus, OperationPlanStatus.APPROVED)
                .set(OperationPlan::getReviewedBy, actor.getUserId())
                .set(OperationPlan::getReviewedAt, now)
                .set(OperationPlan::getApprovedAt, now));
        return getById(planId);
    }

    /**
     * Execution start gate: requires APPROVED. Actual SSH is ticket 08.
     */
    @Transactional(readOnly = true)
    public StartExecutionResponse startExecution(String planId, AuthUserPrincipal actor) {
        OperationPlan plan = requirePlan(planId);
        ConflictCase conflict = requireOpenConflict(plan.getConflictId());
        requireAcceptedHandler(conflict, actor);
        if (plan.getStatus() != OperationPlanStatus.APPROVED) {
            throw new BusinessException("PLAN_NOT_APPROVED",
                    "Cannot start execution before human approval (no execution intent yet)");
        }
        return new StartExecutionResponse(
                planId,
                "EXECUTION_INTENT_READY",
                "Plan approved; SSH execution lands in ticket 08"
        );
    }

    private List<OperationPlanResponse.PlanStep> buildFixActualSteps(ConflictCase conflict) {
        String curatedHostId = conflict.getCuratedTargetId();
        String observedHostId = conflict.getObservedTargetId();
        CuratedObject curatedHost = curatedObjectMapper.selectById(curatedHostId);
        CuratedObject observedHost = observedHostId == null ? null : curatedObjectMapper.selectById(observedHostId);
        String curatedName = curatedHost != null ? curatedHost.getName() : curatedHostId;
        String observedName = observedHost != null ? observedHost.getName() : observedHostId;

        return List.of(
                new OperationPlanResponse.PlanStep(
                        1,
                        "SSH_PRECHECK",
                        "在实际宿主上确认容器仍可操作",
                        Map.of(
                                "hostId", observedHostId == null ? "" : observedHostId,
                                "hostName", observedName == null ? "" : observedName
                        )
                ),
                new OperationPlanResponse.PlanStep(
                        2,
                        "MIGRATE_CONTAINER",
                        "将容器迁回策展宿主 " + curatedName + "（纯修现场，无草案）",
                        Map.of(
                                "fromHostId", observedHostId == null ? "" : observedHostId,
                                "toHostId", curatedHostId,
                                "subjectId", conflict.getSubjectId()
                        )
                ),
                new OperationPlanResponse.PlanStep(
                        3,
                        "REFRESH_OBSERVATION",
                        "执行后刷新观测快照以核验「运行于」",
                        Map.of("subjectId", conflict.getSubjectId())
                )
        );
    }

    private OperationPlan findActive(String conflictId) {
        return operationPlanMapper.selectOne(new LambdaQueryWrapper<OperationPlan>()
                .eq(OperationPlan::getConflictId, conflictId)
                .in(OperationPlan::getStatus, ACTIVE)
                .orderByDesc(OperationPlan::getCreatedAt)
                .last("LIMIT 1"));
    }

    private OperationPlan requirePlan(String planId) {
        OperationPlan plan = operationPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("PLAN_NOT_FOUND", "Operation plan not found: " + planId);
        }
        return plan;
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

    private OperationPlanResponse toResponse(OperationPlan plan) {
        boolean executionIntent = plan.getStatus() == OperationPlanStatus.APPROVED
                || plan.getStatus() == OperationPlanStatus.EXECUTING
                || plan.getStatus() == OperationPlanStatus.COMPLETED;
        return new OperationPlanResponse(
                plan.getId(),
                plan.getConflictId(),
                plan.getDiagnosisId(),
                plan.getSelectedForkId(),
                plan.getBranchKind(),
                Boolean.TRUE.equals(plan.getSkipsDraft()),
                plan.getStatus(),
                readSteps(plan.getStepsJson()),
                plan.getCreatedBy(),
                plan.getCreatedAt(),
                plan.getReviewedBy(),
                plan.getReviewedAt(),
                plan.getApprovedAt(),
                executionIntent
        );
    }

    private String writeSteps(List<OperationPlanResponse.PlanStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<OperationPlanResponse.PlanStep> readSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
