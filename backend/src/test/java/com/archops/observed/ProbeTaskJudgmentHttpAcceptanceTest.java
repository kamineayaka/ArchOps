package com.archops.observed;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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

    @Test
    void readableEqualCuratedValueIsNormalWithoutConflict() throws Exception {
        String hostId = createHost("probe-c-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-c", hostId, readableResult(taskId, true, "ignored")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult got = mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.up", is(true)))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andReturn();
        String responseBody = got.getResponse().getContentAsString();
        assertFalse(responseBody.contains("无法归类"));
        assertFalse(responseBody.contains("RULES_WITH_LLM"));
        assertFalse(responseBody.contains("aiSuggestion"));
    }

    @Test
    void readableUnequalOpensConflictWithEvidenceAndResolutionActions() throws Exception {
        String hostId = createHost("probe-d-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-d", hostId, readableResult(taskId, false, "stdout=down")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult task = mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.observedValue.up", is(false)))
                .andExpect(jsonPath("$.data.evidence", is("stdout=down")))
                .andExpect(jsonPath("$.data.conflictId", notNullValue()))
                .andReturn();
        String conflictId = objectMapper.readTree(task.getResponse().getContentAsString())
                .path("data").path("conflictId").asText();

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)))
                .andExpect(jsonPath("$.data.probeTaskId", is(taskId)))
                .andExpect(jsonPath("$.data.evidence", is("stdout=down")))
                .andExpect(jsonPath("$.data.curatedStructured.up", is(true)))
                .andExpect(jsonPath("$.data.observedStructured.up", is(false)));

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        MvcResult diagnosis = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source", is("RULES")))
                .andExpect(jsonPath("$.data.forks", hasSize(2)))
                .andExpect(jsonPath("$.data.forks[*].id", containsInAnyOrder(
                        "FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='FIX_ACTUAL_TO_CURATED')].label",
                        org.hamcrest.Matchers.hasItem("修实际")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')].label",
                        org.hamcrest.Matchers.hasItem("改理想")))
                .andReturn();
        String diagnosisBody = diagnosis.getResponse().getContentAsString();
        assertFalse(diagnosisBody.contains("\"kind\""));
        assertFalse(diagnosisBody.contains("无法归类"));
        assertFalse(diagnosisBody.contains("RULES_WITH_LLM"));
    }

    @Test
    void unreadableResultIsHollowNotConflict() throws Exception {
        String hostId = createHost("probe-e-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-e", hostId, unreadableResult(taskId)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult got = mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)))
                .andExpect(jsonPath("$.data.observedValue", nullValue()))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.evidence", is("command error")))
                .andReturn();
        String responseBody = got.getResponse().getContentAsString();
        assertFalse(responseBody.contains("\"kind\""));
        assertFalse(responseBody.contains("无法归类"));
        assertFalse(responseBody.contains("abnormalKind"));
    }

    @Test
    void overdueReportIsHollowNotConflict() throws Exception {
        String hostId = createHost("probe-f-host");
        String taskId = createTask(hostId, 0);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-f", hostId, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.probeTasks[0].id", is(taskId)));

        mockMvc.perform(post("/api/probe-tasks/scan-overdue")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hollowedTaskIds", hasItem(taskId)));

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));
    }

    @Test
    void oneTaskHollowDoesNotHollowAnotherTaskOnTheSameHost() throws Exception {
        String hostId = createHost("probe-g-host");
        String hollowTask = createTask(hostId, 60);
        String normalTask = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-g", hostId, joinResults(
                                unreadableResult(hollowTask),
                                readableResult(normalTask, true, "ok"))))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/probe-tasks/{id}", hollowTask)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.evidence", is("command error")));

        mockMvc.perform(get("/api/probe-tasks/{id}", normalTask)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.observedValue.up", is(true)))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));
    }

    @Test
    void secondProbeAgentDoesNotReceiveTasksOrSubmitResults() throws Exception {
        String hostId = createHost("probe-h-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-h-a", hostId, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.probeTasks[0].id", is(taskId)));

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-h-b", hostId, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.probeTasks", hasSize(0)));

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-h-b", hostId, readableResult(taskId, true, "from-b")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PROBE_AGENT_ALREADY_BOUND")));

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-h-a", hostId, readableResult(taskId, true, "from-a")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));
    }

    @Test
    void unreadableAfterConflictSuspendsTheCase() throws Exception {
        String hostId = createHost("probe-i-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-i", hostId, readableResult(taskId, false, "stdout=down")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        String conflictId = objectMapper.readTree(mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("conflictId").asText();

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-i", hostId, unreadableResult(taskId)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("HOLLOW")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()));

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUSPENDED")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)));
    }

    @Test
    void equalAfterConflictMovesToPendingClose() throws Exception {
        String hostId = createHost("probe-j-host");
        String taskId = createTask(hostId, 60);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-j", hostId, readableResult(taskId, false, "stdout=down")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        String conflictId = objectMapper.readTree(mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("conflictId").asText();

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatJson("agent-probe-j", hostId, readableResult(taskId, true, "stdout=up")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.observedValue.up", is(true)));

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.observationHollow", is(false)));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
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

    private ArrayNode joinResults(JsonNode... batches) {
        ArrayNode all = objectMapper.createArrayNode();
        for (JsonNode batch : batches) {
            batch.forEach(all::add);
        }
        return all;
    }

    private JsonNode readableResult(String taskId, boolean up, String evidence) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("taskId", taskId);
        result.put("readable", true);
        result.putObject("value").put("up", up);
        result.put("evidence", evidence);
        return objectMapper.createArrayNode().add(result);
    }

    private JsonNode unreadableResult(String taskId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("taskId", taskId);
        result.put("readable", false);
        result.put("evidence", "command error");
        return objectMapper.createArrayNode().add(result);
    }
}
