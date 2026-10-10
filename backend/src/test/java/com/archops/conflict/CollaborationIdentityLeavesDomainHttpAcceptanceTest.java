package com.archops.conflict;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046 ticket 06: collaboration identity leaves the conflict domain.
 */
@HttpAcceptanceTest
class CollaborationIdentityLeavesDomainHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void claimDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6-a", "id6-b", "ctr-id6-001");

        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration").doesNotExist());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("ACKNOWLEDGED"))))
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("HANDLER_ACCEPTED"))));
    }

    @Test
    void acknowledgeDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6k-a", "id6k-b", "ctr-id6-002");

        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("ACKNOWLEDGED"))));
    }

    @Test
    void selfAppointDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6s-a", "id6s-b", "ctr-id6-003");

        mockMvc.perform(post("/api/conflicts/{id}/acknowledge-and-self-appoint", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("HANDLER_ACCEPTED"))))
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("ACKNOWLEDGED"))));
    }

    @Test
    void assignDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6g-a", "id6g-b", "ctr-id6-004");

        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"user-general-2-demo\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("HANDLER_ASSIGNED"))));
    }

    @Test
    void acceptDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6c-a", "id6c-b", "ctr-id6-005");

        mockMvc.perform(post("/api/conflicts/{id}/accept-handler", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("HANDLER_ACCEPTED"))));
    }

    @Test
    void rejectDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6r-a", "id6r-b", "ctr-id6-006");

        mockMvc.perform(post("/api/conflicts/{id}/reject-handler", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not mine\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("HANDLER_REJECTED"))));
    }

    @Test
    void transferDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6t-a", "id6t-b", "ctr-id6-007");

        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"user-general-2-demo\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration").doesNotExist());

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", not(hasItem("HANDLER_TRANSFER_OFFERED"))));
    }

    @Test
    void assignWithoutIdentityBodyDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6n-a", "id6n-b", "ctr-id6-009");

        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectWithoutIdentityBodyDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6j-a", "id6j-b", "ctr-id6-010");

        mockMvc.perform(post("/api/conflicts/{id}/reject-handler", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferWithoutIdentityBodyDoesNotRecordCollaborationIdentity() throws Exception {
        String conflictId = openConflict("id6u-a", "id6u-b", "ctr-id6-011");

        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration").doesNotExist());
    }

    @Test
    void openPlanDoesNotUseHandlerGateOrReturnHandlerId() throws Exception {
        String conflictId = openConflict("id6p-a", "id6p-b", "ctr-id6-008");

        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handlerUserId").doesNotExist());
    }

    private String openConflict(String hostAName, String hostBName, String objectId) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
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
