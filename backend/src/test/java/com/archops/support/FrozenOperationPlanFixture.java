package com.archops.support;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.conflict.diagnosis.DiagnosisRuleEngine;
import com.archops.plan.domain.OperationPlan;
import com.archops.plan.domain.OperationPlanStatus;
import com.archops.plan.domain.PlanBranchKind;
import com.archops.plan.domain.PlanStepAction;
import com.archops.plan.mapper.OperationPlanMapper;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test-only factory for frozen {@code operation_plan} rows that public HTTP cannot mint.
 *
 * <p>Production never rewrites {@code stepsJson} after create (计划冻结). HTTP always writes
 * non-empty {@code expected} on new 修实际 plans. Spec US9 still needs a pre-B3 JSON shape
 * with no {@code expected} key so start-execution stays exit-code-only. Ticket 08 still
 * needs a first step whose {@code params.hostId} is not graph-resident. Neither shape has
 * a public write path, and this module must not become one: there is no strip-expected
 * or rewrite-frozen-steps HTTP API.
 *
 * <p>Allowed: {@code INSERT} a new APPROVED row whose JSON is already the historical
 * shape. Forbidden: {@code UPDATE} {@code stepsJson} on a plan that HTTP create/approve
 * already froze. Callers still build the surrounding world over HTTP (hosts, 运行于,
 * heartbeat, claim, credentials). {@code POST start-execution} remains the HTTP seam
 * for the behavior.
 */
public final class FrozenOperationPlanFixture {

    private final MockMvc mockMvc;
    private final ObjectMapper json;
    private final OperationPlanMapper plans;

    public FrozenOperationPlanFixture(MockMvc mockMvc, ObjectMapper json, OperationPlanMapper plans) {
        this.mockMvc = mockMvc;
        this.json = json;
        this.plans = plans;
    }

    /**
     * Inserts an APPROVED 修实际 plan whose frozen steps omit {@code expected}.
     * Host params come from the HTTP conflict so start-execution can reach SSH/fake.
     */
    public String insertApprovedWithoutExpected(String conflictId, String actorUserId) throws Exception {
        World world = loadWorld(conflictId, actorUserId);
        return insertApproved(world, stepsJsonWithoutExpected(world));
    }

    private World loadWorld(String conflictId, String actorUserId) throws Exception {
        ConflictDiagnosisWait.waitUntilReady(mockMvc, json, conflictId, actorUserId);
        MvcResult diagnosisResult = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, actorUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String diagnosisId = json.readTree(diagnosisResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        MvcResult conflictResult = mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, actorUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = json.readTree(conflictResult.getResponse().getContentAsString()).path("data");
        String subjectId = data.path("mergeKey").path("subjectId").asText();
        String curatedHostId = data.path("curatedValue").path("hostId").asText();
        String observedHostId = data.path("observedValue").path("hostId").asText();
        String curatedHostName = data.path("curatedValue").path("hostName").asText("");
        String observedHostName = data.path("observedValue").path("hostName").asText("");
        if (diagnosisId.isBlank() || subjectId.isBlank() || curatedHostId.isBlank() || observedHostId.isBlank()) {
            throw new IllegalStateException(
                    "HTTP world incomplete for frozen plan fixture on conflict " + conflictId);
        }
        return new World(
                conflictId,
                diagnosisId,
                actorUserId,
                subjectId,
                curatedHostId,
                observedHostId,
                curatedHostName,
                observedHostName);
    }

    private String insertApproved(World world, String stepsJson) {
        Instant now = Instant.now();
        String planId = "plan-" + UUID.randomUUID();
        OperationPlan plan = new OperationPlan();
        plan.setId(planId);
        plan.setConflictId(world.conflictId());
        plan.setDiagnosisId(world.diagnosisId());
        plan.setSelectedForkId(DiagnosisRuleEngine.FIX_ACTUAL_TO_CURATED);
        plan.setBranchKind(PlanBranchKind.FIX_ACTUAL);
        plan.setSkipsDraft(true);
        plan.setStatus(OperationPlanStatus.APPROVED);
        plan.setStepsJson(stepsJson);
        plan.setCreatedBy(world.actorUserId());
        plan.setCreatedAt(now);
        plan.setReviewedBy(world.actorUserId());
        plan.setReviewedAt(now);
        plan.setApprovedAt(now);
        int rows = plans.insert(plan);
        if (rows != 1) {
            throw new IllegalStateException("Failed to insert frozen plan fixture " + planId);
        }
        return planId;
    }

    private String stepsJsonWithoutExpected(World world) throws Exception {
        ArrayNode steps = json.createArrayNode();
        steps.add(stepWithoutExpected(
                1,
                PlanStepAction.SSH_PRECHECK.name(),
                "在实际宿主上确认容器仍可操作",
                Map.of("hostId", world.observedHostId(), "hostName", world.observedHostName())));
        steps.add(stepWithoutExpected(
                2,
                PlanStepAction.MIGRATE_CONTAINER.name(),
                "将容器迁回策展宿主 " + world.curatedHostName() + "（纯修现场，无草案）",
                Map.of(
                        "fromHostId", world.observedHostId(),
                        "toHostId", world.curatedHostId(),
                        "subjectId", world.subjectId())));
        steps.add(stepWithoutExpected(
                3,
                PlanStepAction.REFRESH_OBSERVATION.name(),
                "执行后刷新观测快照以核验「运行于」",
                Map.of("subjectId", world.subjectId())));
        return json.writeValueAsString(steps);
    }

    private ObjectNode stepWithoutExpected(
            int seq,
            String action,
            String description,
            Map<String, String> params
    ) {
        ObjectNode step = json.createObjectNode();
        step.put("seq", seq);
        step.put("action", action);
        step.put("description", description);
        ObjectNode paramsNode = step.putObject("params");
        params.forEach(paramsNode::put);
        return step;
    }

    private record World(
            String conflictId,
            String diagnosisId,
            String actorUserId,
            String subjectId,
            String curatedHostId,
            String observedHostId,
            String curatedHostName,
            String observedHostName
    ) {
    }
}
