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
import static org.hamcrest.Matchers.not;
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

    /**
     * The bind target's observed host differs from the candidate's host, so a leaked observed
     * `运行于` write (or a promised 升级链) would show up as the 冲突 flipping to 待确认关闭.
     */
    @Test
    void bindingLeavesTheObservedTrackAndTheUpgradeChainUntouched() throws Exception {
        String hostA = createHost("u08d-ha");
        String hostB = createHost("u08d-hb");
        String containerX = createContainer("u08d-x", "u08d-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatLabeled(hostB, "u08d-agb", "u08d-rt-hit", "u08d-x", "u08d-oid");
        heartbeatMissingLabel(hostA, "u08d-aga", "u08d-rt-miss", "u08d-similar");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)));

        OpenUnboundDraft draft = openDraftFromRuntime("u08d-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(bindItem.path("subjectId").asText(), is(containerX));
        postUnboundItem(draft.draftId(), bindItem.path("id").asText(), "accept")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject.objectId", is("u08d-oid")))
                .andExpect(jsonPath("$.data.target.id", is(hostA)));

        assertThat(unboundByRuntimeId(listUnbound(), "u08d-rt-miss"), nullValue());
    }

    @Test
    void secondFieldEntityCannotBeBoundToAnAlreadyBoundTarget() throws Exception {
        String hostA = createHost("u08b-h");
        String containerX = createContainer("u08b-x", "u08b-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatTwoMissingLabels(hostA, "u08b-ag", "u08b-rt-1", "u08b-a", "u08b-rt-2", "u08b-b");

        OpenUnboundDraft first = openDraftFromRuntime("u08b-rt-1");
        OpenUnboundDraft second = openDraftFromRuntime("u08b-rt-2");
        JsonNode firstBind = itemByKind(first.items(), "BIND_UNBOUND_TO_EXISTING");
        JsonNode secondBind = itemByKind(second.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(firstBind.path("subjectId").asText(), is(containerX));
        assertThat(secondBind.path("subjectId").asText(), is(containerX));

        postUnboundItem(first.draftId(), firstBind.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));

        postUnboundItem(second.draftId(), secondBind.path("id").asText(), "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_BIND_TARGET_ALREADY_BOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/curated-drafts/{draftId}", second.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("PENDING")));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject.objectId", is("u08b-oid")))
                .andExpect(jsonPath("$.data.target.id", is(hostA)));

        MvcResult listed = listUnbound();
        assertThat(unboundByRuntimeId(listed, "u08b-rt-1"), nullValue());
        assertThat(unboundByRuntimeId(listed, "u08b-rt-2"), notNullValue());
    }

    @Test
    void eachFieldEntityGetsItsOwnIdentityLostTargetOnTheSameHost() throws Exception {
        String hostA = createHost("u08c-h");
        String containerX = createContainer("u08c-x", "u08c-oid-x");
        String containerY = createContainer("u08c-y", "u08c-oid-y");
        confirmRunsOn(containerX, hostA);
        confirmRunsOn(containerY, hostA);
        heartbeatTwoMissingLabels(hostA, "u08c-ag", "u08c-rt-1", "u08c-a", "u08c-rt-2", "u08c-b");

        OpenUnboundDraft first = openDraftFromRuntime("u08c-rt-1");
        JsonNode firstBind = itemByKind(first.items(), "BIND_UNBOUND_TO_EXISTING");
        String firstTarget = firstBind.path("subjectId").asText();
        assertThat(List.of(containerX, containerY).contains(firstTarget), is(true));
        postUnboundItem(first.draftId(), firstBind.path("id").asText(), "accept")
                .andExpect(status().isOk());

        OpenUnboundDraft second = openDraftFromRuntime("u08c-rt-2");
        JsonNode secondBind = itemByKind(second.items(), "BIND_UNBOUND_TO_EXISTING");
        String secondTarget = secondBind.path("subjectId").asText();
        assertThat(secondTarget, is(not(firstTarget)));
        assertThat(List.of(containerX, containerY).contains(secondTarget), is(true));

        postUnboundItem(second.draftId(), secondBind.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));

        MvcResult listed = listUnbound();
        assertThat(unboundByRuntimeId(listed, "u08c-rt-1"), nullValue());
        assertThat(unboundByRuntimeId(listed, "u08c-rt-2"), nullValue());
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

    private void heartbeatTwoMissingLabels(
            String hostId,
            String agentId,
            String firstRuntimeId,
            String firstName,
            String secondRuntimeId,
            String secondName
    ) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      { "runtimeId": "%s", "name": "%s", "labels": {} },
                                      { "runtimeId": "%s", "name": "%s", "labels": {} }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(agentId, hostId, firstRuntimeId, firstName, secondRuntimeId, secondName))
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
