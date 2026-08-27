package com.archops.conflict;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conflict-upgrade-void-plans ticket 01: 冲突升级作废活跃操作计划.
 * One behavior per method. Unique host/container/agent ids (AFTER_CLASS refresh).
 */
@HttpAcceptanceTest
@TestPropertySource(properties = {
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
class ConflictUpgradeVoidsActivePlanHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void upgradeOpenBtoCVoidsApprovedPlanAndRejectsStartExecution() throws Exception {
        Fixture fx = openClaimAndApprovePlan("cuv01a-a", "cuv01a-b", "cuv01a-oid");
        String hostC = heartbeatObservedOnNewHost(fx, "cuv01a-c");

        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(fx.conflictId())))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.identityLost", is(false)))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostC)))
                .andExpect(jsonPath("$.data.observedLineage", hasSize(2)))
                .andExpect(jsonPath("$.data.observedLineage[0].hostId", is(fx.hostB())))
                .andExpect(jsonPath("$.data.observedLineage[1].hostId", is(hostC)));

        mockMvc.perform(get("/api/operation-plans/{id}", fx.planId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")));

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", fx.planId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_VOIDED")));
    }

    @Test
    void upgradeOpenBtoCWritesPlanVoidedEventWithConflictUpgradeReason() throws Exception {
        Fixture fx = openClaimAndApprovePlan("cuv01b-a", "cuv01b-b", "cuv01b-oid");
        heartbeatObservedOnNewHost(fx, "cuv01b-c");

        mockMvc.perform(get("/api/operation-plans/{id}", fx.planId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.voidReason", is("conflict_upgrade")));

        mockMvc.perform(get("/api/conflicts/{id}/events", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("UPGRADED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("PLAN_VOIDED")))
                .andExpect(jsonPath("$.data[?(@.eventType=='PLAN_VOIDED')].detail.planId",
                        hasItem(fx.planId())))
                .andExpect(jsonPath("$.data[?(@.eventType=='PLAN_VOIDED')].detail.reason",
                        hasItem("conflict_upgrade")));
    }

    private Fixture openClaimAndApprovePlan(String hostAName, String hostBName, String objectId) throws Exception {
        String hostA = createHost(hostAName);
        String hostB = createHost(hostBName);
        String containerId = createContainer("app-" + objectId, objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);

        MvcResult conflictResult = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String conflictId = objectMapper.readTree(conflictResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

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

        return new Fixture(hostA, hostB, objectId, containerId, conflictId, planId);
    }

    private String heartbeatObservedOnNewHost(Fixture fx, String hostCName) throws Exception {
        String hostC = createHost(hostCName);
        heartbeatWithContainer(hostC, "agent-" + fx.objectId() + "-c", fx.objectId());
        return hostC;
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

    private record Fixture(
            String hostA,
            String hostB,
            String objectId,
            String containerId,
            String conflictId,
            String planId
    ) {
    }
}
