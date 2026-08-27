package com.archops.plan.service;

import com.archops.common.exception.BusinessException;
import com.archops.common.lock.PlanExecutionLock;
import com.archops.common.ssh.ControlledSshPort;
import com.archops.common.ssh.SshExecRequest;
import com.archops.common.ssh.SshExecResult;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.diagnosis.DiagnosisRuleEngine;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.DiagnosisStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.conflict.service.ConflictEventService;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Branch selection → review → controlled SSH execution with Redis plan mutex (tickets 07–08).
 */
@Service
public class OperationPlanService {

    private static final List<OperationPlanStatus> ACTIVE = List.of(
            OperationPlanStatus.DRAFT_REVIEW,
            OperationPlanStatus.APPROVED,
            OperationPlanStatus.EXECUTING
    );

    private static final Duration EXEC_LOCK_TTL = Duration.ofMinutes(10);

    private final OperationPlanMapper operationPlanMapper;
    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final CuratedObjectMapper curatedObjectMapper;
    private final ObjectMapper objectMapper;
    private final ControlledSshPort controlledSshPort;
    private final PlanExecutionLock planExecutionLock;
    private final TransactionTemplate transactionTemplate;
    private final ConflictEventService conflictEventService;

    public OperationPlanService(
            OperationPlanMapper operationPlanMapper,
            ConflictCaseMapper conflictCaseMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            CuratedObjectMapper curatedObjectMapper,
            ObjectMapper objectMapper,
            ControlledSshPort controlledSshPort,
            PlanExecutionLock planExecutionLock,
            TransactionTemplate transactionTemplate,
            ConflictEventService conflictEventService
    ) {
        this.operationPlanMapper = operationPlanMapper;
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.curatedObjectMapper = curatedObjectMapper;
        this.objectMapper = objectMapper;
        this.controlledSshPort = controlledSshPort;
        this.planExecutionLock = planExecutionLock;
        this.transactionTemplate = transactionTemplate;
        this.conflictEventService = conflictEventService;
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
        plan.setStepsJson(writeJson(steps));
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
        if (plan.getStatus() == OperationPlanStatus.VOIDED) {
            throw new BusinessException("PLAN_VOIDED",
                    "Voided plans cannot be retried; generate a new plan through review");
        }
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
     * Execute an APPROVED plan step-by-step via the controlled SSH port under a plan mutex.
     * Failure/block voids the plan immediately; steps are frozen (no in-place rewrite/retry).
     */
    public StartExecutionResponse startExecution(String planId, AuthUserPrincipal actor) {
        OperationPlan gate = requirePlan(planId);
        if (gate.getStatus() == OperationPlanStatus.VOIDED) {
            throw new BusinessException("PLAN_VOIDED",
                    "Voided plans cannot be retried; generate a new plan through review");
        }
        if (gate.getStatus() == OperationPlanStatus.COMPLETED) {
            throw new BusinessException("PLAN_ALREADY_FINISHED",
                    "Plan already completed; execution cannot be restarted");
        }
        if (gate.getStatus() == OperationPlanStatus.EXECUTING) {
            throw new BusinessException("PLAN_ALREADY_EXECUTING",
                    "Plan is already executing");
        }
        ConflictCase conflict = requireOpenConflict(gate.getConflictId());
        requireAcceptedHandler(conflict, actor);

        if (gate.getStatus() != OperationPlanStatus.APPROVED) {
            throw new BusinessException("PLAN_NOT_APPROVED",
                    "Cannot start execution before human approval (no execution intent yet)");
        }

        if (!planExecutionLock.tryLock(planId, EXEC_LOCK_TTL)) {
            throw new BusinessException("PLAN_EXECUTION_LOCKED",
                    "Another replica (or worker) holds the execution lock for this plan");
        }

        try {
            Instant startedAt = Instant.now();
            Boolean marked = transactionTemplate.execute(status -> {
                OperationPlan plan = requirePlan(planId);
                if (plan.getStatus() != OperationPlanStatus.APPROVED) {
                    return false;
                }
                int updated = operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                        .eq(OperationPlan::getId, planId)
                        .eq(OperationPlan::getStatus, OperationPlanStatus.APPROVED)
                        .set(OperationPlan::getStatus, OperationPlanStatus.EXECUTING)
                        .set(OperationPlan::getStartedAt, startedAt)
                        .set(OperationPlan::getCurrentStepSeq, 0));
                return updated == 1;
            });
            if (!Boolean.TRUE.equals(marked)) {
                throw new BusinessException("PLAN_ALREADY_EXECUTING",
                        "Plan left APPROVED before this replica could start");
            }

            OperationPlan plan = requirePlan(planId);
            List<OperationPlanResponse.PlanStep> steps = readSteps(plan.getStepsJson());
            List<OperationPlanResponse.ExecutionStepLog> log = new ArrayList<>();

            for (OperationPlanResponse.PlanStep step : steps) {
                transactionTemplate.executeWithoutResult(status ->
                        operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                                .eq(OperationPlan::getId, planId)
                                .set(OperationPlan::getCurrentStepSeq, step.seq())));

                String hostId;
                try {
                    hostId = resolveTargetHostId(step, plan.getConflictId());
                    requireGraphPhysicalHost(hostId);
                } catch (BusinessException ex) {
                    return voidPlan(planId, log, step, null, null, ex.getMessage());
                }

                String command = buildCommand(step, hostId);
                SshExecResult result;
                try {
                    result = controlledSshPort.exec(new SshExecRequest(
                            hostId,
                            command,
                            step.action(),
                            step.seq(),
                            step.params() == null ? Map.of() : step.params()
                    ));
                } catch (BusinessException ex) {
                    return voidPlan(planId, log, step, hostId, command, ex.getMessage());
                } catch (RuntimeException ex) {
                    return voidPlan(planId, log, step, hostId, command,
                            "SSH execution blocked: " + ex.getMessage());
                }
                log.add(new OperationPlanResponse.ExecutionStepLog(
                        step.seq(),
                        step.action(),
                        hostId,
                        command,
                        result.success(),
                        result.failureReason()
                ));
                if (!result.success()) {
                    String reason = result.failureReason() == null
                            ? "SSH step failed: " + step.action()
                            : result.failureReason();
                    return voidPlan(planId, log, step, hostId, command, reason);
                }
            }

            Instant finished = Instant.now();
            String logJson = writeJson(log);
            transactionTemplate.executeWithoutResult(status ->
                    operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                            .eq(OperationPlan::getId, planId)
                            .eq(OperationPlan::getStatus, OperationPlanStatus.EXECUTING)
                            .set(OperationPlan::getStatus, OperationPlanStatus.COMPLETED)
                            .set(OperationPlan::getFinishedAt, finished)
                            .set(OperationPlan::getCurrentStepSeq, steps.isEmpty() ? 0 : steps.getLast().seq())
                            .set(OperationPlan::getExecutionLogJson, logJson)));

            conflictEventService.append(plan.getConflictId(), ConflictEventType.PLAN_COMPLETED, actor.getUserId(), Map.of(
                    "planId", planId,
                    "completedSteps", log.size(),
                    "hint", "Post-exec observation refresh is via agent heartbeat/snapshot (探测写入观测)"
            ));

            return new StartExecutionResponse(
                    planId,
                    OperationPlanStatus.COMPLETED.name(),
                    "All frozen steps executed via controlled SSH; accept observation refresh (heartbeat) to enter 待确认关闭",
                    log.size(),
                    null,
                    List.copyOf(log)
            );
        } finally {
            planExecutionLock.unlock(planId);
        }
    }

    private StartExecutionResponse voidPlan(
            String planId,
            List<OperationPlanResponse.ExecutionStepLog> log,
            OperationPlanResponse.PlanStep step,
            String hostId,
            String command,
            String reason
    ) {
        boolean alreadyLogged = log.stream().anyMatch(e -> e.seq() == step.seq());
        if (!alreadyLogged) {
            log.add(new OperationPlanResponse.ExecutionStepLog(
                    step.seq(),
                    step.action(),
                    hostId,
                    command,
                    false,
                    reason
            ));
        }
        Instant finished = Instant.now();
        String logJson = writeJson(log);
        transactionTemplate.executeWithoutResult(status ->
                operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                        .eq(OperationPlan::getId, planId)
                        .in(OperationPlan::getStatus, List.of(
                                OperationPlanStatus.APPROVED,
                                OperationPlanStatus.EXECUTING
                        ))
                        .set(OperationPlan::getStatus, OperationPlanStatus.VOIDED)
                        .set(OperationPlan::getVoidReason, reason)
                        .set(OperationPlan::getFinishedAt, finished)
                        .set(OperationPlan::getCurrentStepSeq, step.seq())
                        .set(OperationPlan::getExecutionLogJson, logJson)));

        return new StartExecutionResponse(
                planId,
                OperationPlanStatus.VOIDED.name(),
                "Execution stopped; plan voided (no in-place retry)",
                (int) log.stream().filter(OperationPlanResponse.ExecutionStepLog::success).count(),
                reason,
                List.copyOf(log)
        );
    }

    private String resolveTargetHostId(OperationPlanResponse.PlanStep step, String conflictId) {
        Map<String, String> params = step.params() == null ? Map.of() : step.params();
        return switch (step.action()) {
            case "SSH_PRECHECK" -> requiredParam(params, "hostId");
            case "MIGRATE_CONTAINER" -> {
                // Migration is initiated from the observed (actual) host.
                String from = requiredParam(params, "fromHostId");
                // Also validate destination is graph-resident before SSH.
                requireGraphPhysicalHost(requiredParam(params, "toHostId"));
                yield from;
            }
            case "REFRESH_OBSERVATION" -> {
                ConflictCase conflict = requireOpenConflict(conflictId);
                yield conflict.getCuratedTargetId();
            }
            default -> throw new BusinessException("PLAN_STEP_UNKNOWN",
                    "Unknown frozen plan action: " + step.action());
        };
    }

    private static String requiredParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException("PLAN_STEP_HOST_MISSING",
                    "Frozen step missing required host param: " + key);
        }
        return value;
    }

    private void requireGraphPhysicalHost(String hostId) {
        CuratedObject host = curatedObjectMapper.selectById(hostId);
        if (host == null || host.getKind() != CuratedObjectKind.PHYSICAL_HOST) {
            throw new BusinessException("HOST_OFF_GRAPH",
                    "Execution target is not a graph-resident physical host: " + hostId);
        }
    }

    private static String buildCommand(OperationPlanResponse.PlanStep step, String hostId) {
        Map<String, String> params = step.params() == null ? Map.of() : step.params();
        return switch (step.action()) {
            case "SSH_PRECHECK" -> "archops-precheck --host " + hostId;
            case "MIGRATE_CONTAINER" -> "archops-migrate --from " + params.getOrDefault("fromHostId", "")
                    + " --to " + params.getOrDefault("toHostId", "")
                    + " --subject " + params.getOrDefault("subjectId", "");
            case "REFRESH_OBSERVATION" -> "archops-refresh-observation --subject "
                    + params.getOrDefault("subjectId", "")
                    + " --host " + hostId;
            default -> "archops-unknown-action " + step.action();
        };
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

    /**
     * Void all active plans for a conflict (e.g. observation hollow). Frozen plans are not rewritten.
     */
    @Transactional
    public List<String> voidActivePlansForConflict(String conflictId, String reason) {
        List<OperationPlan> active = operationPlanMapper.selectList(new LambdaQueryWrapper<OperationPlan>()
                .eq(OperationPlan::getConflictId, conflictId)
                .in(OperationPlan::getStatus, ACTIVE));
        Instant now = Instant.now();
        List<String> voided = new ArrayList<>();
        for (OperationPlan plan : active) {
            int updated = operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                    .eq(OperationPlan::getId, plan.getId())
                    .in(OperationPlan::getStatus, ACTIVE)
                    .set(OperationPlan::getStatus, OperationPlanStatus.VOIDED)
                    .set(OperationPlan::getVoidReason, reason)
                    .set(OperationPlan::getFinishedAt, now));
            if (updated == 1) {
                voided.add(plan.getId());
            }
        }
        return voided;
    }

    public boolean hasActive(String conflictId) {
        return findActive(conflictId) != null;
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
                executionIntent,
                plan.getCurrentStepSeq(),
                plan.getVoidReason(),
                plan.getStartedAt(),
                plan.getFinishedAt(),
                readExecutionLog(plan.getExecutionLogJson())
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return value instanceof List<?> ? "[]" : "{}";
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

    private List<OperationPlanResponse.ExecutionStepLog> readExecutionLog(String json) {
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
