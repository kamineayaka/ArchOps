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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Collaboration write routes are gone. A no-op 200 is not removal.
 */
@HttpAcceptanceTest
class CollaborationLeftoverRemovedHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void claimOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-claim-a", "rm-claim-b", "ctr-rm-claim");

        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.collaboration").doesNotExist());
    }

    @Test
    void acknowledgeOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-ack-a", "rm-ack-b", "ctr-rm-ack");

        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void selfAppointOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-self-a", "rm-self-b", "ctr-rm-self");

        mockMvc.perform(post("/api/conflicts/{id}/acknowledge-and-self-appoint", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-asg-a", "rm-asg-b", "ctr-rm-asg");

        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"user-general-2-demo\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-acc-a", "rm-acc-b", "ctr-rm-acc");

        mockMvc.perform(post("/api/conflicts/{id}/accept-handler", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-rej-a", "rm-rej-b", "ctr-rm-rej");

        mockMvc.perform(post("/api/conflicts/{id}/reject-handler", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not mine\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferOnOpenConflictIsNotFoundWithoutUser() throws Exception {
        String conflictId = openConflict("rm-tr-a", "rm-tr-b", "ctr-rm-tr");

        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"user-general-2-demo\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void listActiveConflictsWithoutUserOmitsCollaboration() throws Exception {
        openConflict("rm-list-a", "rm-list-b", "ctr-rm-list");

        mockMvc.perform(get("/api/conflicts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].collaboration").doesNotExist())
                .andExpect(jsonPath("$.data[0].ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.data[0].handlerUserId").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    void getConflictWithoutUserOmitsCollaboration() throws Exception {
        String conflictId = openConflict("rm-get-a", "rm-get-b", "ctr-rm-get");

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration").doesNotExist())
                .andExpect(jsonPath("$.data.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.data.handlerUserId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.identityLost").value(false));
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
