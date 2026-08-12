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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 04 HTTP acceptance: conflict warn on curated≠observed; merge-key upgrade B→C.
 */
@HttpAcceptanceTest
class ConflictWarnUpgradeHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void curatedAObservedBCreatesOpenWarningWithoutDiagnosis() throws Exception {
        String hostA = createHost("cnf-a");
        String hostB = createHost("cnf-b");
        String containerId = createContainer("app-cnf", "ctr-cnf-001");
        confirmRunsOn(containerId, hostA);

        heartbeatWithContainer(hostB, "agent-cnf-b", "ctr-cnf-001");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .param("relationType", "RUNS_ON")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", startsWith("cnf-")))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.diagnosisStatus", is("NOT_STARTED")))
                .andExpect(jsonPath("$.data.mergeKey.subjectId", is(containerId)))
                .andExpect(jsonPath("$.data.mergeKey.relationType", is("RUNS_ON")))
                .andExpect(jsonPath("$.data.mergeKey.relationLabel", is("运行于")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.observedLineage", hasSize(1)));

        assertEquals(1, countOpenForSubject(containerId));
    }

    @Test
    void observedBtoCUpgradesSameConflictWithLineage() throws Exception {
        String hostA = createHost("up-a");
        String hostB = createHost("up-b");
        String hostC = createHost("up-c");
        String containerId = createContainer("app-up", "ctr-up-001");
        confirmRunsOn(containerId, hostA);

        heartbeatWithContainer(hostB, "agent-up-b", "ctr-up-001");

        MvcResult first = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andReturn();
        String conflictId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("id").asText();

        heartbeatWithContainer(hostC, "agent-up-c", "ctr-up-001");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(conflictId)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostC)))
                .andExpect(jsonPath("$.data.observedLineage", hasSize(2)))
                .andExpect(jsonPath("$.data.observedLineage[0].hostId", is(hostB)))
                .andExpect(jsonPath("$.data.observedLineage[1].hostId", is(hostC)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.diagnosisStatus", is("NOT_STARTED")));

        assertEquals(1, countOpenForSubject(containerId));
    }

    @Test
    void hollowObservationDoesNotOpenBothSidesConflict() throws Exception {
        String hostA = createHost("hollow-a");
        String containerId = createContainer("app-hollow-cnf", "ctr-hollow-cnf");
        confirmRunsOn(containerId, hostA);

        // Heartbeat-only (no snapshot) → freshness only, no observed fact → 空洞, no conflict.
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"agent-hollow","hostId":"%s"}
                                """.formatted(hostA))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        assertEquals(0, countOpenForSubject(containerId));
    }

    private int countOpenForSubject(String subjectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/conflicts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        int count = 0;
        for (JsonNode node : data) {
            if (subjectId.equals(node.path("mergeKey").path("subjectId").asText())) {
                count++;
            }
        }
        return count;
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
