package com.archops.plan;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.executor.ExecutorEngineHandle;
import com.archops.executor.ExecutorEngineTestConfig;
import com.archops.plan.domain.OperationPlan;
import com.archops.plan.mapper.OperationPlanMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * plan-step-assertion 01: engine judges frozen expected; detailed results land on executionLog.
 */
@HttpAcceptanceTest
@TestPropertySource(properties = {
        "archops.ssh.mode=dispatch",
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
@Import(ExecutorEngineTestConfig.class)
class PlanStepAssertionHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutorEngineHandle engine;

    @Autowired
    private OperationPlanMapper operationPlanMapper;

    @BeforeEach
    void resetEngineFake() {
        engine.fakeSsh().clear();
    }

    @Test
    void approvedFixActualPlanCarriesExpectedAndCompletesWhenEngineJsonContainsIt() throws Exception {
        String conflictId = openConflictAndClaim("psa1-a", "psa1-b", "ctr-psa1");
        String planId = selectAndApprove(conflictId);

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps", hasSize(3)))
                .andExpect(jsonPath("$.data.steps[0].action", is("SSH_PRECHECK")))
                .andExpect(jsonPath("$.data.steps[0].expected.precheck", is("passed")))
                .andExpect(jsonPath("$.data.steps[1].action", is("MIGRATE_CONTAINER")))
                .andExpect(jsonPath("$.data.steps[1].expected.migrated", is("true")))
                .andExpect(jsonPath("$.data.steps[2].action", is("REFRESH_OBSERVATION")))
                .andExpect(jsonPath("$.data.steps[2].expected.refresh", is("ok")));

        MvcResult executed = mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.completedSteps", is(3)))
                .andExpect(jsonPath("$.data.executionLog", hasSize(3)))
                .andReturn();

        JsonNode startLog = objectMapper.readTree(executed.getResponse().getContentAsString())
                .path("data").path("executionLog");
        assertJsonContains(startLog.get(0).path("structuredOutput").asText(), "precheck", "passed");
        assertJsonContains(startLog.get(1).path("structuredOutput").asText(), "migrated", "true");
        assertJsonContains(startLog.get(2).path("structuredOutput").asText(), "refresh", "ok");

        MvcResult fetched = mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.steps[0].expected.precheck", is("passed")))
                .andExpect(jsonPath("$.data.executionLog", hasSize(3)))
                .andReturn();
        JsonNode getLog = objectMapper.readTree(fetched.getResponse().getContentAsString())
                .path("data").path("executionLog");
        assertJsonContains(getLog.get(0).path("structuredOutput").asText(), "precheck", "passed");
        assertJsonContains(getLog.get(1).path("structuredOutput").asText(), "migrated", "true");
        assertJsonContains(getLog.get(2).path("structuredOutput").asText(), "refresh", "ok");
    }

    @Test
    void exitSuccessWithMismatchedJsonVoidsPlanAsStepAssertionFailedAndBlocksRetry() throws Exception {
        String conflictId = openConflictAndClaim("psa2-a", "psa2-b", "ctr-psa2");
        String planId = selectAndApprove(conflictId);
        engine.fakeSsh().succeedWithStdout("SSH_PRECHECK", "{\"precheck\":\"failed\",\"source\":\"fake\"}");

        MvcResult executed = mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.executionLog", hasSize(1)))
                .andReturn();
        JsonNode data = objectMapper.readTree(executed.getResponse().getContentAsString()).path("data");
        assertThat(data.path("voidReason").asText()).startsWith("STEP_ASSERTION_FAILED");
        JsonNode log0 = data.path("executionLog").get(0);
        assertThat(log0.path("success").asBoolean()).isFalse();
        assertThat(log0.path("failureReason").asText()).startsWith("STEP_ASSERTION_FAILED");
        assertJsonContains(log0.path("structuredOutput").asText(), "precheck", "failed");
        assertThat(engine.recordedCalls()).hasSize(1);

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", startsWith("STEP_ASSERTION_FAILED")))
                .andExpect(jsonPath("$.data.executionLog[0].failureReason",
                        startsWith("STEP_ASSERTION_FAILED")));

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_VOIDED")));
    }

    @Test
    void exitSuccessWithNonJsonStructuredOutputVoidsPlanAsStepAssertionFailed() throws Exception {
        String conflictId = openConflictAndClaim("psa3-a", "psa3-b", "ctr-psa3");
        String planId = selectAndApprove(conflictId);
        engine.fakeSsh().succeedWithStdout("SSH_PRECHECK", "precheck log line, not a JSON object");

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", startsWith("STEP_ASSERTION_FAILED")))
                .andExpect(jsonPath("$.data.executionLog[0].success", is(false)))
                .andExpect(jsonPath("$.data.executionLog[0].failureReason", startsWith("STEP_ASSERTION_FAILED")))
                .andExpect(jsonPath("$.data.executionLog[0].structuredOutput",
                        is("precheck log line, not a JSON object")));

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", startsWith("STEP_ASSERTION_FAILED")));
    }

    @Test
    void fakeExitFailureVoidsPlanAsSshFailureNotStepAssertion() throws Exception {
        String conflictId = openConflictAndClaim("psa4-a", "psa4-b", "ctr-psa4");
        String planId = selectAndApprove(conflictId);
        engine.fakeSsh().failOnAction("SSH_PRECHECK");

        MvcResult executed = mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.executionLog[0].success", is(false)))
                .andReturn();
        JsonNode data = objectMapper.readTree(executed.getResponse().getContentAsString()).path("data");
        String voidReason = data.path("voidReason").asText();
        String failureReason = data.path("executionLog").get(0).path("failureReason").asText();
        assertThat(voidReason).isNotBlank();
        assertThat(voidReason).doesNotStartWith("STEP_ASSERTION_FAILED");
        assertThat(failureReason).isNotBlank();
        assertThat(failureReason).doesNotStartWith("STEP_ASSERTION_FAILED");
        assertThat(engine.recordedCalls()).hasSize(1);
        assertThat(engine.recordedCalls().getFirst().success()).isFalse();

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")));
    }

    @Test
    void planJsonWithoutExpectedStillCompletesOnExitCodeOnly() throws Exception {
        String conflictId = openConflictAndClaim("psa5-a", "psa5-b", "ctr-psa5");
        String planId = selectAndApprove(conflictId);
        stripExpectedFromStoredSteps(planId);
        engine.fakeSsh().succeedWithStdout("SSH_PRECHECK", "not-json and would fail assertion");
        engine.fakeSsh().succeedWithStdout("MIGRATE_CONTAINER", "{\"migrated\":\"false\"}");
        engine.fakeSsh().succeedWithStdout("REFRESH_OBSERVATION", "{\"refresh\":\"nope\"}");

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.completedSteps", is(3)))
                .andExpect(jsonPath("$.data.executionLog", hasSize(3)))
                .andExpect(jsonPath("$.data.executionLog[0].structuredOutput",
                        is("not-json and would fail assertion")));

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.executionLog[0].success", is(true)));
    }

    private void stripExpectedFromStoredSteps(String planId) throws Exception {
        OperationPlan plan = operationPlanMapper.selectById(planId);
        ArrayNode steps = (ArrayNode) objectMapper.readTree(plan.getStepsJson());
        for (JsonNode step : steps) {
            ((ObjectNode) step).remove("expected");
        }
        operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                .eq(OperationPlan::getId, planId)
                .set(OperationPlan::getStepsJson, objectMapper.writeValueAsString(steps)));
    }

    private void assertJsonContains(String structuredOutput, String key, String value) throws Exception {
        assertThat(structuredOutput).isNotBlank();
        JsonNode object = objectMapper.readTree(structuredOutput);
        assertThat(object.isObject()).isTrue();
        assertThat(object.path(key).isTextual()).isTrue();
        assertThat(object.path(key).asText()).isEqualTo(value);
    }

    private String selectAndApprove(String conflictId) throws Exception {
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        MvcResult created = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andReturn();
        String planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();
        mockMvc.perform(post("/api/operation-plans/{id}/approve", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")));
        return planId;
    }

    private String openConflictAndClaim(String hostAName, String hostBName, String objectId) throws Exception {
        String hostA = createHost(hostAName);
        String hostB = createHost(hostBName);
        storeHostCredential(hostA);
        storeHostCredential(hostB);
        String containerId = createContainer("app-" + objectId, objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);
        MvcResult result = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String conflictId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        return conflictId;
    }

    private void storeHostCredential(String hostId) throws Exception {
        mockMvc.perform(put("/api/curated/hosts/{id}/ssh-credential", hostId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectHost":"10.0.0.8",
                                  "connectPort":22,
                                  "username":"ops",
                                  "secret":"fixture-ssh-secret",
                                  "secretKind":"PASSWORD"
                                }
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void heartbeatWithContainer(String hostId, String agentId, String objectId) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"docker-x",
                                      "name":"app",
                                      "labels":{"archops.object_id":"%s"}
                                    }]
                                  }
                                }
                                """.formatted(agentId, hostId, objectId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private String createHost(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/hosts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return readDataId(result);
    }

    private String createContainer(String name, String objectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"objectId\":\"" + objectId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return readDataId(result);
    }

    private void confirmRunsOn(String containerId, String hostId) throws Exception {
        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerId\":\"" + containerId + "\",\"hostId\":\"" + hostId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private String readDataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }
}
