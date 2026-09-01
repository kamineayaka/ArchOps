package com.archops.plan;

import com.archops.common.ssh.MinaSshPort;
import com.archops.common.ssh.SshCallRecord;
import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.executor.ExecutorEngineHandle;
import com.archops.executor.ExecutorEngineTestConfig;
import com.archops.observed.domain.HostAgent;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * control-plane-executor 01: start-execution dispatches one frozen step at a time to the
 * 执行引擎 fake; control-plane production MINA is not used.
 */
@HttpAcceptanceTest
@TestPropertySource(properties = {
        "archops.ssh.mode=dispatch",
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
@Import(ExecutorEngineTestConfig.class)
class ExecutorSingleStepDispatchHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutorEngineHandle engine;

    @Autowired
    private HostAgentMapper hostAgentMapper;

    @Autowired
    private ObjectProvider<MinaSshPort> controlPlaneMina;

    @BeforeEach
    void resetEngineFake() {
        engine.fakeSsh().clear();
    }

    @Test
    void startExecutionDispatchesFrozenStepsToEngineFakeWithoutControlPlaneMina() throws Exception {
        String conflictId = openConflictAndClaim("e01a-a", "e01a-b", "ctr-e01a");
        String planId = selectAndApprove(conflictId);

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.completedSteps", is(3)))
                .andExpect(jsonPath("$.data.executionLog", hasSize(3)));

        assertThat(controlPlaneMina.getIfAvailable()).isNull();

        List<SshCallRecord> calls = engine.recordedCalls();
        assertThat(calls).hasSize(3);
        assertThat(calls.get(0).stepSeq()).isEqualTo(1);
        assertThat(calls.get(0).action()).isEqualTo("SSH_PRECHECK");
        assertThat(calls.get(0).hostId()).isNotBlank();
        assertThat(calls.get(1).stepSeq()).isEqualTo(2);
        assertThat(calls.get(1).action()).isEqualTo("MIGRATE_CONTAINER");
        assertThat(calls.get(1).hostId()).isEqualTo(calls.get(0).hostId());
        assertThat(calls.get(2).stepSeq()).isEqualTo(3);
        assertThat(calls.get(2).action()).isEqualTo("REFRESH_OBSERVATION");
        assertThat(calls).allMatch(SshCallRecord::success);

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));
    }

    @Test
    void hollowDuringExecutionStopsNextDispatchAndLeavesPlanVoided() throws Exception {
        String objectId = "ctr-e01b";
        String conflictId = openConflictAndClaim("e01b-a", "e01b-b", objectId);
        String planId = selectAndApprove(conflictId);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        engine.fakeSsh().armBlock(entered, release);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> executionStatus = pool.submit(() -> {
                MvcResult result = mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                                .accept(MediaType.APPLICATION_JSON))
                        .andReturn();
                return objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("status").asText();
            });

            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            hostAgentMapper.update(null, new LambdaUpdateWrapper<HostAgent>()
                    .eq(HostAgent::getAgentId, "agent-" + objectId)
                    .set(HostAgent::getLastHeartbeatAt, Instant.now().minus(2, ChronoUnit.MINUTES)));

            mockMvc.perform(post("/api/observed/scan-heartbeat-timeouts")
                            .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.voidedPlanIds", hasItem(planId)));

            engine.fakeSsh().releaseBlock();
            assertThat(executionStatus.get(30, TimeUnit.SECONDS)).isEqualTo("VOIDED");
        } finally {
            engine.fakeSsh().releaseBlock();
            pool.shutdownNow();
        }

        assertThat(engine.recordedCalls()).hasSize(1);
        assertThat(engine.recordedCalls().getFirst().action()).isEqualTo("SSH_PRECHECK");

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", is("observation_hollow_heartbeat_timeout")));

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_VOIDED")));
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
