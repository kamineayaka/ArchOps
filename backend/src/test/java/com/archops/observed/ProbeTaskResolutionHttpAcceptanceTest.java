package com.archops.observed;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Probe conflict resolution for the thin UI: 改理想 opens a draft and does not write 策展.
 */
@HttpAcceptanceTest
class ProbeTaskResolutionHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String COMMAND = "printf '{\"up\":true}'";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void changeCuratedOnProbeConflictOpensDraftWithoutWritingCurated() throws Exception {
        String hostId = createHost("probe-ui-draft-host");
        String taskId = createTask(hostId);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatWithUnequal("agent-probe-ui-draft", taskId, hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        String conflictId = objectMapper.readTree(mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data").path("conflictId").asText();

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"CHANGE_CURATED_TO_OBSERVED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.origin", is("CHANGE_CURATED")))
                .andExpect(jsonPath("$.data.selectedForkId", is("CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.createdBy", nullValue()))
                .andExpect(jsonPath("$.data.items[0].status", is("PENDING")));

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedExpected.up", is(true)))
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")));
    }

    @Test
    void fixActualOnProbeConflictOpensReviewPlan() throws Exception {
        String hostId = createHost("probe-ui-plan-host");
        String taskId = createTask(hostId);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatWithUnequal("agent-probe-ui-plan", taskId, hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        String conflictId = objectMapper.readTree(mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data").path("conflictId").asText();

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.selectedForkId", is("FIX_ACTUAL_TO_CURATED")))
                .andExpect(jsonPath("$.data.createdBy", nullValue()));

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedExpected.up", is(true)));
    }

    private String createHost(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/hosts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String createTask(String hostId) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("hostId", hostId);
        request.put("command", COMMAND);
        request.put("intervalSeconds", 60);
        request.putObject("curatedExpected").put("up", true);
        MvcResult result = mockMvc.perform(post("/api/probe-tasks")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String heartbeatWithUnequal(String agentId, String taskId, String hostId) {
        return """
                {
                  "agentId": "%s",
                  "hostId": "%s",
                  "probeResults": [{
                    "taskId": "%s",
                    "readable": true,
                    "value": {"up": false},
                    "evidence": "stdout=down"
                  }]
                }
                """.formatted(agentId, hostId, taskId);
    }
}
