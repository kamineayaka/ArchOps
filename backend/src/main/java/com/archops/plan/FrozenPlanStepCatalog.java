package com.archops.plan;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.plan.domain.PlanStepAction;
import com.archops.plan.dto.OperationPlanResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Frozen FIX_ACTUAL step catalog: expected maps, step list, SSH host resolution.
 * Execution cursor and lock stay on OperationPlanService.
 */
@Component
public class FrozenPlanStepCatalog {

    static final Map<String, String> EXPECTED_PRECHECK = Map.of("precheck", "passed");
    static final Map<String, String> EXPECTED_MIGRATED = Map.of("migrated", "true");
    static final Map<String, String> EXPECTED_REFRESH = Map.of("refresh", "ok");

    private final CuratedObjectMapper curatedObjectMapper;
    private final ConflictCaseMapper conflictCaseMapper;

    public FrozenPlanStepCatalog(
            CuratedObjectMapper curatedObjectMapper,
            ConflictCaseMapper conflictCaseMapper
    ) {
        this.curatedObjectMapper = curatedObjectMapper;
        this.conflictCaseMapper = conflictCaseMapper;
    }

    public List<OperationPlanResponse.PlanStep> buildFixActualSteps(ConflictCase conflict) {
        String curatedHostId = conflict.getCuratedTargetId();
        String observedHostId = conflict.getObservedTargetId();
        CuratedObject curatedHost = curatedObjectMapper.selectById(curatedHostId);
        CuratedObject observedHost = observedHostId == null ? null : curatedObjectMapper.selectById(observedHostId);
        String curatedName = curatedHost != null ? curatedHost.getName() : curatedHostId;
        String observedName = observedHost != null ? observedHost.getName() : observedHostId;
        return fixActualSteps(
                conflict.getSubjectId(),
                curatedHostId,
                curatedName,
                observedHostId,
                observedName
        );
    }

    static List<OperationPlanResponse.PlanStep> fixActualSteps(
            String subjectId,
            String curatedHostId,
            String curatedName,
            String observedHostId,
            String observedName
    ) {
        return List.of(
                new OperationPlanResponse.PlanStep(
                        1,
                        PlanStepAction.SSH_PRECHECK.name(),
                        "在实际宿主上确认容器仍可操作",
                        Map.of(
                                "hostId", observedHostId == null ? "" : observedHostId,
                                "hostName", observedName == null ? "" : observedName
                        ),
                        EXPECTED_PRECHECK
                ),
                new OperationPlanResponse.PlanStep(
                        2,
                        PlanStepAction.MIGRATE_CONTAINER.name(),
                        "将容器迁回策展宿主 " + curatedName + "（纯修现场，无草案）",
                        Map.of(
                                "fromHostId", observedHostId == null ? "" : observedHostId,
                                "toHostId", curatedHostId,
                                "subjectId", subjectId
                        ),
                        EXPECTED_MIGRATED
                ),
                new OperationPlanResponse.PlanStep(
                        3,
                        PlanStepAction.REFRESH_OBSERVATION.name(),
                        "执行后刷新观测快照以核验「运行于」",
                        Map.of("subjectId", subjectId),
                        EXPECTED_REFRESH
                )
        );
    }

    public String resolveTargetHostId(OperationPlanResponse.PlanStep step, String conflictId) {
        Map<String, String> params = step.params() == null ? Map.of() : step.params();
        return switch (PlanStepAction.parse(step.action())) {
            case SSH_PRECHECK -> requiredParam(params, "hostId");
            case MIGRATE_CONTAINER -> {
                // Migration is initiated from the observed (actual) host.
                String from = requiredParam(params, "fromHostId");
                // Also validate destination is graph-resident before SSH.
                requireGraphPhysicalHost(requiredParam(params, "toHostId"));
                yield from;
            }
            case REFRESH_OBSERVATION -> requireOpenConflict(conflictId).getCuratedTargetId();
        };
    }

    public void requireGraphPhysicalHost(String hostId) {
        CuratedObject host = curatedObjectMapper.selectById(hostId);
        if (host == null || host.getKind() != CuratedObjectKind.PHYSICAL_HOST) {
            throw new BusinessException("HOST_OFF_GRAPH",
                    "Execution target is not a graph-resident physical host: " + hostId);
        }
    }

    static String resolveParamHost(
            PlanStepAction action,
            Map<String, String> params,
            String refreshHostId
    ) {
        return switch (action) {
            case SSH_PRECHECK -> requiredParam(params, "hostId");
            case MIGRATE_CONTAINER -> requiredParam(params, "fromHostId");
            case REFRESH_OBSERVATION -> refreshHostId;
        };
    }

    static String requiredParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException("PLAN_STEP_HOST_MISSING",
                    "Frozen step missing required host param: " + key);
        }
        return value;
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
}
