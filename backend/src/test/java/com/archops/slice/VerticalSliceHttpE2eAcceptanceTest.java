package com.archops.slice;

import com.archops.common.ssh.RecordingFakeSshPort;
import com.archops.common.ssh.SshCallRecord;
import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.observed.domain.HostAgent;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 13 — vertical-slice HTTP primary-seam acceptance (ordered happy path + negatives).
 * SSH fake is a supporting double only; assertions stay on HTTP responses / HTTP-readable state.
 */
@HttpAcceptanceTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
class VerticalSliceHttpE2eAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingFakeSshPort fakeSsh;

    @Autowired
    private HostAgentMapper hostAgentMapper;

    @BeforeEach
    void resetFakeSsh() {
        fakeSsh.clear();
    }

    @Test
    @Order(1)
    void happyPath_curatedA_snapshotOnB_claim_fixActual_sshFake_pendingClose_confirm() throws Exception {
        String objectId = "ctr-e2e-happy";
        String hostA = createHost("e2e-host-a");
        String hostB = createHost("e2e-host-b");
        String containerId = createContainer("app-e2e-happy", objectId);

        // 策展：X 运行于 A；规范问法「应该在哪」
        confirmRunsOn(containerId, hostA);
        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        // Agent 快照：实际运行于 B → 冲突警告可先于诊断完成
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);

        MvcResult warn = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .param("relationType", "RUNS_ON")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", startsWith("cnf-")))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.mergeKey.relationLabel", is("运行于")))
                .andExpect(jsonPath("$.data.diagnosisStatus", anyOf(is("PENDING"), is("READY"), is("NOT_STARTED"))))
                .andReturn();
        String conflictId = objectMapper.readTree(warn.getResponse().getContentAsString())
                .path("data").path("id").asText();
        assertTrue(conflictId.startsWith("cnf-"));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        // 认领 → 已接受处理人
        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_ID)));

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.forks[*].id", hasItem("FIX_ACTUAL_TO_CURATED")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='FIX_ACTUAL_TO_CURATED')].kind", hasItem("FIX_ACTUAL")));

        // Non-handler cannot open plan via branch selection
        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

        // 选「修实际回 A」→ 人审前不可执行
        MvcResult created = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.selectedForkId", is("FIX_ACTUAL_TO_CURATED")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)))
                .andExpect(jsonPath("$.data.executionIntent", is(false)))
                .andExpect(jsonPath("$.data.steps", hasSize(3)))
                .andReturn();
        String planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_APPROVED")));
        assertThat(fakeSsh.recordedCalls()).isEmpty();

        mockMvc.perform(post("/api/operation-plans/{id}/approve", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.executionIntent", is(true)));

        // SSH fake 执行（支撑双；副作用经后续 HTTP 可读）
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

        // 观测回到 A → 待确认关闭 → 处理人确认
        heartbeatWithContainer(hostA, "agent-" + objectId + "-refresh", objectId);

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.pendingCloseReminderVisible", is(true)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostA)));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")))
                .andExpect(jsonPath("$.data.closedAt", notNullValue()));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("WARNED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("HANDLER_ACCEPTED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("PLAN_COMPLETED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("CLOSED")));
    }

    @Test
    @Order(2)
    void negative_heartbeatTimeoutSuspendsConflictAndVoidsActivePlan() throws Exception {
        String objectId = "ctr-e2e-hollow";
        String hostA = createHost("e2e-hollow-a");
        String hostB = createHost("e2e-hollow-b");
        String containerId = createContainer("app-e2e-hollow", objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);

        String conflictId = conflictIdBySubject(containerId);
        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

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

        mockMvc.perform(post("/api/operation-plans/{id}/approve", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")));

        hostAgentMapper.update(null, new LambdaUpdateWrapper<HostAgent>()
                .eq(HostAgent::getAgentId, "agent-" + objectId)
                .set(HostAgent::getLastHeartbeatAt, Instant.now().minus(2, ChronoUnit.MINUTES)));

        mockMvc.perform(post("/api/observed/scan-heartbeat-timeouts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspendedConflictIds", hasItem(conflictId)))
                .andExpect(jsonPath("$.data.voidedPlanIds", hasItem(planId)));

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUSPENDED")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("HOLLOW")));

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
        assertThat(fakeSsh.recordedCalls()).isEmpty();
    }

    @Test
    @Order(3)
    void negative_sensitiveBusinessReadIsRejected() throws Exception {
        mockMvc.perform(post("/api/workbench/sensitive-reads")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"business_db.orders","intent":"READ_CUSTOMER_ORDERS"}
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("SENSITIVE_BUSINESS_READ_DENIED")));
    }

    @Test
    @Order(4)
    void negative_unlabeledSnapshotDoesNotPromiseUpgradeChain() throws Exception {
        String hostB = createHost("e2e-unb-b");
        String containerId = createContainer("app-e2e-lost", "ctr-e2e-lost");
        confirmRunsOn(containerId, hostB);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "agent-e2e-unb",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "docker-mystery",
                                        "name": "mystery",
                                        "labels": {}
                                      },
                                      {
                                        "runtimeId": "docker-unknown",
                                        "name": "other",
                                        "labels": { "archops.object_id": "never-curated-e2e" }
                                      }
                                    ],
                                    "identityLostObjectIds": ["ctr-e2e-lost"]
                                  }
                                }
                                """.formatted(hostB))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unbound", hasSize(2)))
                .andExpect(jsonPath("$.data.unbound[0].upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.unbound[1].upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.identityLost", hasSize(1)))
                .andExpect(jsonPath("$.data.identityLost[0].upgradeChainPromised", is(false)));

        // No merge-key conflict promised for unlabeled/unbound path.
        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.upgradeChainPromised==true)]").isEmpty());

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")))
                .andExpect(jsonPath("$.data.curatedObjectId", is(containerId)));
    }

    private String conflictIdBySubject(String containerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
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
