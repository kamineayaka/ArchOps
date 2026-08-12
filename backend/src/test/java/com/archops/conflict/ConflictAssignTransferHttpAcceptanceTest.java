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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 11 HTTP acceptance: assign / accept / reject / transfer handler (Should).
 */
@HttpAcceptanceTest
class ConflictAssignTransferHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String GENERAL_2_ID = "user-general-2-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void seniorAssignsGeneralPendingCannotOpenPlanUntilAccept() throws Exception {
        String conflictId = openConflict("asg-a", "asg-b", "ctr-asg-001");

        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("NONE")));

        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + GENERAL_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")));

        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

        mockMvc.perform(post("/api/conflicts/{id}/accept-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")))
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)));

        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN_INTENT_ACCEPTED")))
                .andExpect(jsonPath("$.data.handlerUserId", is(GENERAL_ID)));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("HANDLER_ASSIGNED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("HANDLER_ACCEPTED")));
    }

    @Test
    void pendingHandlerRejectRequiresReasonAndClearsHandlerKeepsOwner() throws Exception {
        String conflictId = openConflict("rej-a", "rej-b", "ctr-rej-001");
        acknowledgeAndAssign(conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/reject-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/conflicts/{id}/reject-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"当前值班冲突，无法接手\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.acknowledged", is(true)))
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", nullValue()))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("NONE")));

        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

        // Owner may re-assign after reject.
        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + GENERAL_2_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_2_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")))
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("HANDLER_REJECTED")));
    }

    @Test
    void transferChangesHandlerNotOwnerAndRequiresRecipientConsent() throws Exception {
        String conflictId = openConflict("tr-a", "tr-b", "ctr-tr-001");
        acknowledgeAndAssign(conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/accept-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")));

        // Owner cannot force reassign while accepted handler exists.
        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + GENERAL_2_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_HANDLER_EXISTS")));

        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + GENERAL_2_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_2_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")));

        // Previous handler lost plan gate; pending recipient cannot open yet.
        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_2_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

        mockMvc.perform(post("/api/conflicts/{id}/accept-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_2_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_2_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")))
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)));

        mockMvc.perform(post("/api/conflicts/{id}/operation-plans", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_2_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handlerUserId", is(GENERAL_2_ID)));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("HANDLER_TRANSFER_OFFERED")));
    }

    @Test
    void pendingHandlerMayTransferBeforeAccept() throws Exception {
        String conflictId = openConflict("pt-a", "pt-b", "ctr-pt-001");
        acknowledgeAndAssign(conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + GENERAL_2_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_2_ID)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")))
                .andExpect(jsonPath("$.data.collaboration.ownerUserId", is(SENIOR_ID)));

        mockMvc.perform(post("/api/conflicts/{id}/accept-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_PENDING_HANDLER")));
    }

    @Test
    void cannotAssignSeniorOrNonOwner() throws Exception {
        String conflictId = openConflict("bad-a", "bad-b", "ctr-bad-001");
        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + SENIOR_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_ASSIGNEE_INVALID")));

        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + GENERAL_2_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_ASSIGN_ROLE_DENIED")));
    }

    private void acknowledgeAndAssign(String conflictId, String assigneeUserId) throws Exception {
        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + assigneeUserId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")));
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched", hasSize(1)));
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
