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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unbound ticket 05 HTTP acceptance: 身份失联 gates on 修实际 / 改理想 pipeline.
 * One behavior per method. Unique host/container/agent/runtime ids (AFTER_CLASS refresh).
 */
@HttpAcceptanceTest
class IdentityLostPipelineGateHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openConflictSubjectThenIdentityLostKeepsOpenWithoutPresentingStaleHost() throws Exception {
        String hostA = createHost("u05a-ha");
        String hostB = createHost("u05a-hb");
        String containerId = createContainer("u05a-x", "u05a-oid");
        confirmRunsOn(containerId, hostA);
        heartbeatLabeled(hostB, "u05a-ag-b", "u05a-rt-b", "u05a-x", "u05a-oid");

        MvcResult open = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andReturn();
        String conflictId = objectMapper.readTree(open.getResponse().getContentAsString())
                .path("data").path("id").asText();

        heartbeatUnlabeled(hostB, "u05a-ag-lost", "u05a-rt-miss", "u05a-miss");

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.status", not("SUSPENDED")))
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.availability", not("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.availability", not("HOLLOW")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(conflictId)))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.availability", not("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));
    }

    @Test
    void pendingCloseSubjectThenIdentityLostReturnsToOpenNotSuspended() throws Exception {
        String hostA = createHost("u05b-ha");
        String hostB = createHost("u05b-hb");
        String containerId = createContainer("u05b-x", "u05b-oid");
        confirmRunsOn(containerId, hostA);
        heartbeatLabeled(hostB, "u05b-ag-b", "u05b-rt-b", "u05b-x", "u05b-oid");

        MvcResult open = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        String conflictId = objectMapper.readTree(open.getResponse().getContentAsString())
                .path("data").path("id").asText();

        heartbeatLabeled(hostA, "u05b-ag-a", "u05b-rt-a", "u05b-x", "u05b-oid");
        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));

        heartbeatUnlabeled(hostA, "u05b-ag-lost", "u05b-rt-miss", "u05b-miss");

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.status", not("SUSPENDED")))
                .andExpect(jsonPath("$.data.status", not("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.pendingCloseAt").value(nullValue()))
                .andExpect(jsonPath("$.data.observedValue.availability", not("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));
    }

    private void heartbeatLabeled(
            String hostId, String agentId, String runtimeId, String name, String objectId) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"%s",
                                      "name":"%s",
                                      "labels":{"archops.object_id":"%s"}
                                    }]
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name, objectId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void heartbeatUnlabeled(String hostId, String agentId, String runtimeId, String name)
            throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"%s",
                                      "name":"%s",
                                      "labels":{}
                                    }],
                                    "absentObjectIds":[]
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name))
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
