package com.archops.e2e;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.observed.domain.HostAgent;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 13 — vertical-slice HTTP primary-seam e2e (happy path + required negatives).
 * <p>
 * Assertions stay on public HTTP responses / subsequent HTTP-readable state.
 * SSH uses the fake port (support double, not a second acceptance seam).
 * HostAgent timestamp backdate is CI clock control only — not Redis/MyBatis key-shape asserts.
 */
@HttpAcceptanceTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "archops.ssh.mode=fake",
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
    private HostAgentMapper hostAgentMapper;

    @Test
    @Order(1)
    void happyPath_curatedA_observedB_claim_fixActual_approve_exec_pendingClose_confirm() throws Exception {
        // 1) Curate hosts A/B + container X runs-on A
        String hostA = createHost("e2e-a");
        String hostB = createHost("e2e-b");
        String objectId = "ctr-e2e-happy";
        String containerId = createContainer("app-" + objectId, objectId);
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        // 2) Agent snapshot: X observed on B → conflict warn (must not wait for diagnosis)
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);

        MvcResult warned = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .param("relationType", "RUNS_ON")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", startsWith("cnf-")))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.diagnosisStatus", anyOf(is("PENDING"), is("READY"), is("NOT_STARTED"))))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.mergeKey.relationLabel", is("运行于")))
                .andReturn();
        String conflictId = objectMapper.readTree(warned.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Dual-track ask surfaces while still open
        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        // 3) Claim → accepted handler
        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_ID)));

        // Non-handler cannot select branch (viewer may still read diagnosis once ready)
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.forks[0].id", is("FIX_ACTUAL_TO_CURATED")))
                .andExpect(jsonPath("$.data.forks[0].kind", is("FIX_ACTUAL")));

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

        // 4) Select 修实际回 A → draft plan → approve → fake SSH execute
        MvcResult planCreated = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.selectedForkId", is("FIX_ACTUAL_TO_CURATED")))
                .andExpect(jsonPath("$.data.executionIntent", is(false)))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)))
                .andExpect(jsonPath("$.data.steps", hasSize(3)))
                .andReturn();
        String planId = objectMapper.readTree(planCreated.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_APPROVED")));

        mockMvc.perform(post("/api/operation-plans/{id}/approve", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.executionIntent", is(true)));

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.completedSteps", is(3)));

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        // 5) Observation returns to A → PENDING_CLOSE (equality alone does not auto-close)
        heartbeatWithContainer(hostA, "agent-" + objectId + "-aligned", objectId);

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.pendingCloseReminderVisible", is(true)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostA)));

        MvcResult activePending = mockMvc.perform(get("/api/conflicts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(activeIds(activePending)).contains(conflictId);

        // Non-handler cannot confirm close
        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER")));

        // 6) Accepted handler confirms close
        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")))
                .andExpect(jsonPath("$.data.closedAt", notNullValue()));

        MvcResult afterClose = mockMvc.perform(get("/api/conflicts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(activeIds(afterClose)).doesNotContain(conflictId);

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
    void negative_heartbeatTimeoutHollowSuspendsConflictAndVoidsActivePlan() throws Exception {
        String hostA = createHost("e2e-hollow-a");
        String hostB = createHost("e2e-hollow-b");
        String objectId = "ctr-e2e-hollow";
        String containerId = createContainer("app-" + objectId, objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);

        String conflictId = conflictIdBySubject(containerId);
        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        MvcResult planCreated = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String planId = objectMapper.readTree(planCreated.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/operation-plans/{id}/approve", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")));

        // CI clock control: backdate agent heartbeat past TTL, then drive hollow via HTTP scan
        hostAgentMapper.update(null, new LambdaUpdateWrapper<HostAgent>()
                .eq(HostAgent::getAgentId, "agent-" + objectId)
                .set(HostAgent::getLastHeartbeatAt, Instant.now().minus(2, ChronoUnit.MINUTES)));

        mockMvc.perform(post("/api/observed/scan-heartbeat-timeouts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staleAgents", is(1)))
                .andExpect(jsonPath("$.data.hollowedFacts", is(1)))
                .andExpect(jsonPath("$.data.suspendedConflictIds", hasItem(conflictId)))
                .andExpect(jsonPath("$.data.voidedPlanIds", hasItem(planId)));

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUSPENDED")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("HOLLOW")))
                .andExpect(jsonPath("$.data.observedValue.hostId", nullValue()));

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

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("SUSPENDED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("PLAN_VOIDED")));
    }

    @Test
    @Order(3)
    void negative_sensitiveBusinessReadIsRejectedNotApprovalGated() throws Exception {
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
        String hostB = createHost("e2e-unb-host");
        String objectId = "ctr-e2e-lost";
        String containerId = createContainer("app-" + objectId, objectId);
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
                                    "identityLostObjectIds": ["%s"]
                                  }
                                }
                                """.formatted(hostB, objectId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unbound", hasSize(2)))
                .andExpect(jsonPath("$.data.unbound[0].upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.unbound[1].upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.identityLost", hasSize(1)))
                .andExpect(jsonPath("$.data.identityLost[0].upgradeChainPromised", is(false)));

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
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));

        // Never-curated / unlabeled candidates are not merge-key conflict subjects
        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", "never-curated-e2e")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));
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

    private List<String> activeIds(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        List<String> ids = new ArrayList<>();
        for (JsonNode n : data) {
            ids.add(n.path("id").asText());
        }
        return ids;
    }
}
