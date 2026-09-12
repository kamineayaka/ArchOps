package com.archops.plan;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.plan.domain.PlanStepAction;
import com.archops.plan.dto.OperationPlanResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrozenPlanStepCatalogTest {

    @Test
    void fixActualStepsAreTheThreeFrozenToolsWithExpectedMaps() {
        List<OperationPlanResponse.PlanStep> steps = FrozenPlanStepCatalog.fixActualSteps(
                "ctr-1", "host-curated", "策展宿主", "host-obs", "实际宿主");

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).seq()).isEqualTo(1);
        assertThat(steps.get(0).action()).isEqualTo(PlanStepAction.SSH_PRECHECK.name());
        assertThat(steps.get(0).description()).isEqualTo("在实际宿主上确认容器仍可操作");
        assertThat(steps.get(0).params()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "hostId", "host-obs",
                "hostName", "实际宿主"
        ));
        assertThat(steps.get(0).expected()).containsExactlyInAnyOrderEntriesOf(Map.of("precheck", "passed"));

        assertThat(steps.get(1).seq()).isEqualTo(2);
        assertThat(steps.get(1).action()).isEqualTo(PlanStepAction.MIGRATE_CONTAINER.name());
        assertThat(steps.get(1).description()).isEqualTo("将容器迁回策展宿主 策展宿主（纯修现场，无草案）");
        assertThat(steps.get(1).params()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "fromHostId", "host-obs",
                "toHostId", "host-curated",
                "subjectId", "ctr-1"
        ));
        assertThat(steps.get(1).expected()).containsExactlyInAnyOrderEntriesOf(Map.of("migrated", "true"));

        assertThat(steps.get(2).seq()).isEqualTo(3);
        assertThat(steps.get(2).action()).isEqualTo(PlanStepAction.REFRESH_OBSERVATION.name());
        assertThat(steps.get(2).description()).isEqualTo("执行后刷新观测快照以核验「运行于」");
        assertThat(steps.get(2).params()).containsExactlyInAnyOrderEntriesOf(Map.of("subjectId", "ctr-1"));
        assertThat(steps.get(2).expected()).containsExactlyInAnyOrderEntriesOf(Map.of("refresh", "ok"));
    }

    @Test
    void fixActualStepsBlankObservedHostWhenTargetMissing() {
        List<OperationPlanResponse.PlanStep> steps = FrozenPlanStepCatalog.fixActualSteps(
                "ctr-1", "host-curated", "host-curated", null, null);

        assertThat(steps.get(0).params()).containsEntry("hostId", "").containsEntry("hostName", "");
        assertThat(steps.get(1).params()).containsEntry("fromHostId", "");
    }

    @Test
    void sshPrecheckResolvesHostIdParam() {
        assertThat(FrozenPlanStepCatalog.resolveParamHost(
                PlanStepAction.SSH_PRECHECK,
                Map.of("hostId", "host-obs"),
                "host-curated"))
                .isEqualTo("host-obs");
    }

    @Test
    void migrateResolvesFromHostId() {
        assertThat(FrozenPlanStepCatalog.resolveParamHost(
                PlanStepAction.MIGRATE_CONTAINER,
                Map.of("fromHostId", "host-from", "toHostId", "host-to"),
                "host-curated"))
                .isEqualTo("host-from");
    }

    @Test
    void refreshResolvesCuratedTargetHost() {
        assertThat(FrozenPlanStepCatalog.resolveParamHost(
                PlanStepAction.REFRESH_OBSERVATION,
                Map.of("subjectId", "ctr-1"),
                "host-curated"))
                .isEqualTo("host-curated");
    }

    @Test
    void missingRequiredHostParamThrowsPlanStepHostMissing() {
        assertThatThrownBy(() -> FrozenPlanStepCatalog.requiredParam(Map.of(), "hostId"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Frozen step missing required host param: hostId")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_STEP_HOST_MISSING");
        assertThatThrownBy(() -> FrozenPlanStepCatalog.requiredParam(Map.of("hostId", "  "), "hostId"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Frozen step missing required host param: hostId")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_STEP_HOST_MISSING");
    }

    @Test
    void unknownActionOnResolveThrowsPlanStepUnknown() {
        OperationPlanResponse.PlanStep step = new OperationPlanResponse.PlanStep(
                1, "NOT_A_FROZEN_ACTION", "x", Map.of(), Map.of());
        FrozenPlanStepCatalog catalog = new FrozenPlanStepCatalog(null, null);

        assertThatThrownBy(() -> catalog.resolveTargetHostId(step, "cnf-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown frozen plan action: NOT_A_FROZEN_ACTION")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_STEP_UNKNOWN");
    }

    @Test
    void buildFixActualStepsReadsHostNamesFromConflictTargets() {
        ConflictCase conflict = new ConflictCase();
        conflict.setSubjectId("ctr-1");
        conflict.setCuratedTargetId("host-curated");
        conflict.setObservedTargetId("host-obs");

        List<OperationPlanResponse.PlanStep> steps = FrozenPlanStepCatalog.fixActualSteps(
                conflict.getSubjectId(),
                conflict.getCuratedTargetId(),
                "策展宿主",
                conflict.getObservedTargetId(),
                "实际宿主");

        assertThat(steps.get(1).params()).containsEntry("toHostId", "host-curated");
        assertThat(steps.get(1).params()).containsEntry("fromHostId", "host-obs");
    }
}
