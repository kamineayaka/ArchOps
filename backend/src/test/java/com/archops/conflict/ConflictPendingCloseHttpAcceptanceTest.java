package com.archops.conflict;

import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.mapper.ObservedFactMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 09: post-exec observation refresh → PENDING_CLOSE → handler confirm-close + audit trail.
 */
@HttpAcceptanceTest
class ConflictPendingCloseHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObservedFactMapper observedFactMapper;

    @Test
    void executeThenRefreshObservationEntersPendingCloseAndHandlerConfirms() throws Exception {
        Fixture fx = openClaimPlanApproveAndExecute("p9-a", "p9-b", "ctr-p9-ok");

        heartbeatWithContainer(fx.hostA(), "agent-" + fx.objectId() + "-refresh", fx.objectId());

        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.pendingCloseReminderVisible", is(true)))
                .andExpect(jsonPath("$.data.collaboration.acknowledged", is(true)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(fx.hostA())))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())));

        MvcResult list = mockMvc.perform(get("/api/conflicts")
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(activeIds(list)).contains(fx.conflictId());
        assertThat(statusOf(list, fx.conflictId())).isEqualTo("PENDING_CLOSE");

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", fx.conflictId())
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
        assertThat(activeIds(afterClose)).doesNotContain(fx.conflictId());

        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));

        mockMvc.perform(get("/api/conflicts/{id}/events", fx.conflictId())
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
    void confirmCloseFailsWhenTracksNoLongerEqual() throws Exception {
        Fixture fx = openClaimPlanApproveAndExecute("p9d-a", "p9d-b", "ctr-p9-drift");

        heartbeatWithContainer(fx.hostA(), "agent-" + fx.objectId() + "-a", fx.objectId());
        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));

        // Race: observed drifted while case remains PENDING_CLOSE.
        observedFactMapper.update(null, new LambdaUpdateWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, fx.containerId())
                .set(ObservedFact::getAvailability, ObservedAvailability.PRESENT)
                .set(ObservedFact::getTargetId, fx.hostB()));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_ALIGNED")))
                .andExpect(jsonPath("$.message", containsString("刷新")));

        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(fx.hostB())));

        mockMvc.perform(get("/api/conflicts/{id}/events", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("CONFIRM_FAILED")));
    }

    @Test
    void driftAfterPendingCloseReopensConflictViaHeartbeat() throws Exception {
        Fixture fx = openClaimPlanApproveAndExecute("p9r-a", "p9r-b", "ctr-p9-reopen");
        heartbeatWithContainer(fx.hostA(), "agent-" + fx.objectId() + "-a", fx.objectId());
        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));

        heartbeatWithContainer(fx.hostB(), "agent-" + fx.objectId() + "-b", fx.objectId());
        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(fx.hostB())));
    }

    private Fixture openClaimPlanApproveAndExecute(String hostAName, String hostBName, String objectId)
            throws Exception {
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
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/operation-plans/{id}/start-execution", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        return new Fixture(hostA, hostB, objectId, containerId, conflictId, planId);
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

    private String statusOf(MvcResult result, String conflictId) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode n : data) {
            if (conflictId.equals(n.path("id").asText())) {
                return n.path("status").asText();
            }
        }
        return null;
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
