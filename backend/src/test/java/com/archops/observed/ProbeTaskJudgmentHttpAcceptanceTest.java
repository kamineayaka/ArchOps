package com.archops.observed;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0048 ticket 01: control plane stores probe tasks and judges returned results.
 */
@HttpAcceptanceTest
class ProbeTaskJudgmentHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String COMMAND = "printf '{\"up\":true}'";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void storesProbeTaskOnHostWithNoAgentAsHollowNotConflict() throws Exception {
        String hostId = createHost("probe-a-host");

        ObjectNode request = objectMapper.createObjectNode();
        request.put("hostId", hostId);
        request.put("command", COMMAND);
        request.put("intervalSeconds", 60);
        request.putObject("curatedExpected").put("up", true);

        MvcResult created = mockMvc.perform(post("/api/probe-tasks")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", org.hamcrest.Matchers.is(true)))
                .andExpect(jsonPath("$.data.hostId", org.hamcrest.Matchers.is(hostId)))
                .andExpect(jsonPath("$.data.command", org.hamcrest.Matchers.is(COMMAND)))
                .andExpect(jsonPath("$.data.intervalSeconds", org.hamcrest.Matchers.is(60)))
                .andExpect(jsonPath("$.data.curatedExpected.up", org.hamcrest.Matchers.is(true)))
                .andExpect(jsonPath("$.data.judgment", org.hamcrest.Matchers.is("HOLLOW")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andReturn();

        String taskId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String responseBody = created.getResponse().getContentAsString();
        assertFalse(responseBody.contains("\"kind\""));
        assertFalse(responseBody.contains("无法归类"));
        assertFalse(responseBody.contains("abnormalKind"));
        assertFalse(responseBody.contains("causeKind"));

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", org.hamcrest.Matchers.is(taskId)))
                .andExpect(jsonPath("$.data.command", org.hamcrest.Matchers.is(COMMAND)))
                .andExpect(jsonPath("$.data.curatedExpected.up", org.hamcrest.Matchers.is(true)))
                .andExpect(jsonPath("$.data.judgment", org.hamcrest.Matchers.is("HOLLOW")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.observationHollow", is(true)));
    }

    @Test
    void heartbeatPullsProbeTasksForThatHost() throws Exception {
        String hostId = createHost("probe-b-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-b", hostId, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.probeTasks[0].id", is(taskId)))
                .andExpect(jsonPath("$.data.probeTasks[0].command", is(COMMAND)))
                .andExpect(jsonPath("$.data.probeTasks[0].intervalSeconds", is(60)))
                .andExpect(jsonPath("$.data.probeTasks[0].curatedExpected.up", is(true)));
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

    private String createTask(String hostId, int intervalSeconds) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("hostId", hostId);
        request.put("command", COMMAND);
        request.put("intervalSeconds", intervalSeconds);
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

    private String heartbeatJson(String agentId, String hostId, JsonNode probeResults) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("agentId", agentId);
        request.put("hostId", hostId);
        if (probeResults != null) {
            request.set("probeResults", probeResults);
        }
        return objectMapper.writeValueAsString(request);
    }
}
