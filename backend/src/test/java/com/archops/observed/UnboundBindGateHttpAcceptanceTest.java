package com.archops.observed;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 08 HTTP acceptance: 接受绑定的写入门禁。
 * 失联判据 = 失联之后是否又标签命中；同一策展对象只能是一个现场实体的本体。
 */
@HttpAcceptanceTest
class UnboundBindGateHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bindingToTargetThatLostItsLabelAfterAnEarlierMatchSucceeds() throws Exception {
        String hostA = createHost("u08a-h");
        String containerX = createContainer("u08a-x", "u08a-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatLabeled(hostA, "u08a-ag", "u08a-rt-hit", "u08a-x", "u08a-oid");
        heartbeatMissingLabel(hostA, "u08a-ag", "u08a-rt-miss", "u08a-similar");

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));

        OpenUnboundDraft draft = openDraftFromRuntime("u08a-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(bindItem.path("subjectId").asText(), is(containerX));

        postUnboundItem(draft.draftId(), bindItem.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject.id", is(containerX)))
                .andExpect(jsonPath("$.data.subject.objectId", is("u08a-oid")))
                .andExpect(jsonPath("$.data.target.id", is(hostA)));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));

        MvcResult listed = listUnbound();
        assertThat(unboundByRuntimeId(listed, "u08a-rt-miss"), nullValue());

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));
    }

    private ResultActions postUnboundItem(String draftId, String itemId, String action) throws Exception {
        return mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/{action}",
                        draftId, itemId, action)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON));
    }

    private void heartbeatLabeled(
            String hostId,
            String agentId,
            String runtimeId,
            String name,
            String objectId
    ) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "%s",
                                      "name": "%s",
                                      "labels": { "archops.object_id": "%s" }
                                    }]
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name, objectId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void heartbeatMissingLabel(String hostId, String agentId, String runtimeId, String name) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "%s",
                                      "name": "%s",
                                      "labels": {}
                                    }],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private MvcResult listUnbound() throws Exception {
        return mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
    }

    private OpenUnboundDraft openDraftFromRuntime(String runtimeId) throws Exception {
        JsonNode candidate = unboundByRuntimeId(listUnbound(), runtimeId);
        assertThat(candidate, notNullValue());
        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts",
                        candidate.path("id").asText())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        return new OpenUnboundDraft(data.path("id").asText(), sortedItems(data.path("items")));
    }

    private JsonNode itemByKind(List<JsonNode> items, String kind) {
        return items.stream()
                .filter(node -> kind.equals(node.path("kind").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing item kind " + kind));
    }

    private List<JsonNode> sortedItems(JsonNode itemsNode) {
        List<JsonNode> items = new ArrayList<>();
        itemsNode.forEach(items::add);
        items.sort(Comparator.comparingInt(node -> node.path("seq").asInt()));
        return items;
    }

    private JsonNode unboundByRuntimeId(MvcResult listed, String runtimeId) throws Exception {
        JsonNode data = objectMapper.readTree(listed.getResponse().getContentAsString()).path("data");
        for (JsonNode node : data) {
            if (runtimeId.equals(node.path("runtimeId").asText())) {
                return node;
            }
        }
        return null;
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

    private String createContainer(String name, String objectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"objectId\":\"" + objectId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private void confirmRunsOn(String containerId, String hostId) throws Exception {
        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerId\":\"" + containerId + "\",\"hostId\":\"" + hostId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private record OpenUnboundDraft(String draftId, List<JsonNode> items) {
    }
}
