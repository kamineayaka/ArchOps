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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accepting an already-open probe 改理想 item writes 策展 and re-judges.
 */
@HttpAcceptanceTest
class ProbeCuratedItemAcceptHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String COMMAND = "printf '{\"up\":true}'";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void acceptProbeDraftItemWritesObservedValueAsCuratedExpected() throws Exception {
        OpenProbeDraft draft = openProbeChangeCuratedDraft("probe-accept-host", "agent-probe-accept");

        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", draft.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forks", hasSize(2)))
                .andExpect(jsonPath("$.data.forks[*].id", containsInAnyOrder(
                        "FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")));

        mockMvc.perform(post("/api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept",
                        draft.conflictId(), draft.itemId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status", is("ACCEPTED")))
                .andExpect(jsonPath("$.data.items[0].kind", is("PROBE_CURATED_VALUE")))
                .andExpect(jsonPath("$.data.createdBy", nullValue()));

        mockMvc.perform(get("/api/probe-tasks/{id}", draft.taskId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedExpected.up", is(false)));

        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", draft.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forks", hasSize(2)))
                .andExpect(jsonPath("$.data.forks[*].id", containsInAnyOrder(
                        "FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")));
    }

    @Test
    void acceptRejudgesLatestResultToPendingCloseWithoutClosing() throws Exception {
        OpenProbeDraft draft = openProbeChangeCuratedDraft("probe-rejudge-host", "agent-probe-rejudge");
        acceptItem(draft);

        mockMvc.perform(get("/api/conflicts/{id}", draft.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.curatedStructured.up", is(false)))
                .andExpect(jsonPath("$.data.observedStructured.up", is(false)));

        mockMvc.perform(get("/api/probe-tasks/{id}", draft.taskId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("NORMAL")))
                .andExpect(jsonPath("$.data.conflictId", nullValue()))
                .andExpect(jsonPath("$.data.curatedExpected.up", is(false)))
                .andExpect(jsonPath("$.data.observedValue.up", is(false)));
    }

    @Test
    void confirmCloseAfterEqualRejudgeClosesTheProbeConflict() throws Exception {
        OpenProbeDraft draft = openProbeChangeCuratedDraft("probe-close-host", "agent-probe-close");
        acceptItem(draft);

        mockMvc.perform(get("/api/conflicts/{id}", draft.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", draft.conflictId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
    }

    private void acceptItem(OpenProbeDraft draft) throws Exception {
        mockMvc.perform(post("/api/conflicts/{id}/curated-drafts/open/items/{itemId}/accept",
                        draft.conflictId(), draft.itemId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status", is("ACCEPTED")));
    }

    private OpenProbeDraft openProbeChangeCuratedDraft(String hostName, String agentId) throws Exception {
        String hostId = createHost(hostName);
        String taskId = createTask(hostId);
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeat(agentId, taskId, hostId, false, "stdout=down"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        String conflictId = objectMapper.readTree(mockMvc.perform(get("/api/probe-tasks/{id}", taskId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment", is("CONFLICT")))
                .andExpect(jsonPath("$.data.curatedExpected.up", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data").path("conflictId").asText();
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        MvcResult opened = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"CHANGE_CURATED_TO_OBSERVED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.items[0].status", is("PENDING")))
                .andExpect(jsonPath("$.data.items[0].kind", is("PROBE_CURATED_VALUE")))
                .andReturn();
        String itemId = objectMapper.readTree(opened.getResponse().getContentAsString())
                .path("data").path("items").path(0).path("id").asText();
        return new OpenProbeDraft(taskId, hostId, conflictId, itemId, agentId);
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

    private String heartbeat(String agentId, String taskId, String hostId, boolean up, String evidence) {
        return """
                {
                  "agentId": "%s",
                  "hostId": "%s",
                  "probeResults": [{
                    "taskId": "%s",
                    "readable": true,
                    "value": {"up": %s},
                    "evidence": "%s"
                  }]
                }
                """.formatted(agentId, hostId, taskId, up, evidence);
    }

    private record OpenProbeDraft(
            String taskId,
            String hostId,
            String conflictId,
            String itemId,
            String agentId
    ) {
    }
}
