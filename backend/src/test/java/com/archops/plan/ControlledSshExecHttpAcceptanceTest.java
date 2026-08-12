package com.archops.plan;

import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.common.ssh.SshCallRecord;
import com.archops.conflict.ConflictDiagnosisWait;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 08 HTTP acceptance: controlled SSH fake execution, Redis/in-memory plan mutex,
 * fail→void, off-graph rejection, encrypted host credentials.
 */
@HttpAcceptanceTest
class ControlledSshExecHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingFakeSshPort fakeSsh;

    @Autowired
    private OperationPlanMapper operationPlanMapper;

    @BeforeEach
    void resetFake() {
        fakeSsh.clear();
    }

    @Test
    void approvedPlanExecutesViaFakeSshWithRecordedCalls() throws Exception {
        String conflictId = openConflictAndClaim("p8-a", "p8-b", "ctr-p8-ok");
        String planId = selectAndApprove(conflictId);

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.completedSteps", is(3)))
                .andExpect(jsonPath("$.data.executionLog", hasSize(3)));

        List<SshCallRecord> calls = fakeSsh.recordedCalls();
        assertThat(calls).hasSize(3);
        assertThat(calls.get(0).action()).isEqualTo("SSH_PRECHECK");
        assertThat(calls.get(1).action()).isEqualTo("MIGRATE_CONTAINER");
        assertThat(calls.get(2).action()).isEqualTo("REFRESH_OBSERVATION");
        assertThat(calls).allMatch(SshCallRecord::success);

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.executionLog", hasSize(3)))
                .andExpect(jsonPath("$.data.finishedAt", notNullValue()));

        // Completed plan is no longer "active".
        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));

        // No in-place restart.
        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_ALREADY_FINISHED")));
    }

    @Test
    void unapprovedPlanCannotExecute() throws Exception {
        String conflictId = openConflictAndClaim("p8u-a", "p8u-b", "ctr-p8-unapproved");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        MvcResult created = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_APPROVED")));
        assertThat(fakeSsh.recordedCalls()).isEmpty();
    }

    @Test
    void stepFailureVoidsPlanAndBlocksRetry() throws Exception {
        String conflictId = openConflictAndClaim("p8f-a", "p8f-b", "ctr-p8-fail");
        String planId = selectAndApprove(conflictId);
        fakeSsh.failOnAction("MIGRATE_CONTAINER");

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", notNullValue()))
                .andExpect(jsonPath("$.data.completedSteps", is(1)));

        assertThat(fakeSsh.recordedCalls()).hasSize(2);
        assertThat(fakeSsh.recordedCalls().get(1).success()).isFalse();

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", notNullValue()));

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_VOIDED")));

        // Fresh plan can be opened after void (no in-place rewrite).
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")));
    }

    @Test
    void offGraphHostTargetVoidsPlan() throws Exception {
        String conflictId = openConflictAndClaim("p8o-a", "p8o-b", "ctr-p8-off");
        String planId = selectAndApprove(conflictId);

        OperationPlan plan = operationPlanMapper.selectById(planId);
        ArrayNode steps = (ArrayNode) objectMapper.readTree(plan.getStepsJson());
        ObjectNode first = (ObjectNode) steps.get(0);
        ObjectNode params = (ObjectNode) first.get("params");
        params.put("hostId", "host-not-in-graph");
        operationPlanMapper.update(null, new LambdaUpdateWrapper<OperationPlan>()
                .eq(OperationPlan::getId, planId)
                .set(OperationPlan::getStepsJson, objectMapper.writeValueAsString(steps)));

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", org.hamcrest.Matchers.containsString("graph-resident")));

        assertThat(fakeSsh.recordedCalls()).isEmpty();
    }

    @Test
    void concurrentExecutionBlockedByPlanLock() throws Exception {
        String conflictId = openConflictAndClaim("p8l-a", "p8l-b", "ctr-p8-lock");
        String planId = selectAndApprove(conflictId);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fakeSsh.armBlock(entered, release);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<String> firstStatus = new AtomicReference<>();
        AtomicReference<String> secondCode = new AtomicReference<>();
        try {
            Future<?> first = pool.submit(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                                    .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andReturn();
                    firstStatus.set(objectMapper.readTree(result.getResponse().getContentAsString())
                            .path("data").path("status").asText());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                                    .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isBadRequest())
                            .andReturn();
                    secondCode.set(objectMapper.readTree(result.getResponse().getContentAsString())
                            .path("code").asText());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            second.get(10, TimeUnit.SECONDS);
            fakeSsh.releaseBlock();
            first.get(30, TimeUnit.SECONDS);
        } finally {
            fakeSsh.releaseBlock();
            pool.shutdownNow();
        }

        assertThat(firstStatus.get()).isEqualTo("COMPLETED");
        assertThat(secondCode.get()).isIn("PLAN_EXECUTION_LOCKED", "PLAN_ALREADY_EXECUTING");
    }

    @Test
    void hostSshCredentialStoredEncryptedNeverEchoed() throws Exception {
        String hostId = createHost("p8-cred-host");
        String secret = "s3cret-should-not-leak";

        MvcResult upsert = mockMvc.perform(put("/api/curated/hosts/{id}/ssh-credential", hostId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectHost":"10.0.0.8",
                                  "connectPort":22,
                                  "username":"ops",
                                  "secret":"%s",
                                  "secretKind":"PASSWORD"
                                }
                                """.formatted(secret))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostId", is(hostId)))
                .andExpect(jsonPath("$.data.connectHost", is("10.0.0.8")))
                .andExpect(jsonPath("$.data.username", is("ops")))
                .andExpect(jsonPath("$.data.configured", is(true)))
                .andExpect(jsonPath("$.data.secret").doesNotExist())
                .andExpect(jsonPath("$.data.secretCiphertext").doesNotExist())
                .andReturn();

        String body = upsert.getResponse().getContentAsString();
        assertThat(body).doesNotContain(secret);

        mockMvc.perform(get("/api/curated/hosts/{id}/ssh-credential", hostId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured", is(true)))
                .andExpect(jsonPath("$.data.secret").doesNotExist());

        // Off-graph / container id rejected.
        String containerId = createContainer("cred-ctr", "ctr-p8-cred-ctr");
        mockMvc.perform(put("/api/curated/hosts/{id}/ssh-credential", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectHost":"10.0.0.9",
                                  "username":"ops",
                                  "secret":"x",
                                  "secretKind":"PASSWORD"
                                }
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("HOST_OFF_GRAPH")));
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
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }
}
