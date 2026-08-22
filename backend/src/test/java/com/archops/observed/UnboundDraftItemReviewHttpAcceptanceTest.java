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
 * Ticket 03 HTTP acceptance: per-item accept/reject on 未绑定草案.
 * One behavior per method; witnessed red → green → refactor.
 */
@HttpAcceptanceTest
class UnboundDraftItemReviewHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedOperatorAcceptsCreateAndRejectsRunsOnOnUnknownDraft() throws Exception {
        String hostId = createHost("u03a-h");
        heartbeatUnknown(hostId, "u03a-ag", "u03a-rt-unknown", "u03a-unknown", "u03a-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u03a-rt-unknown");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode runsOnItem = itemByKind(draft.items(), "CURATED_RUNS_ON_INSERT");
        String createItemId = createItem.path("id").asText();
        String runsOnItemId = runsOnItem.path("id").asText();

        MvcResult accepted = postUnboundItem(draft.draftId(), createItemId, "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + createItemId + "')].status",
                        hasItem("ACCEPTED")))
                .andReturn();
        JsonNode acceptedCreate = itemByKind(
                sortedItems(objectMapper.readTree(accepted.getResponse().getContentAsString())
                        .path("data").path("items")),
                "CREATE_CONTAINER_FROM_UNBOUND");
        String createdSubjectId = acceptedCreate.path("subjectId").asText();
        assertThat(createdSubjectId, notNullValue());
        assertThat(createdSubjectId.isBlank(), is(false));
        assertThat(acceptedCreate.path("subjectId").isNull(), is(false));

        postUnboundItem(draft.draftId(), runsOnItemId, "reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + runsOnItemId + "')].status",
                        hasItem("REJECTED")));

        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"u03a-probe\",\"objectId\":\"u03a-never\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_OBJECT_ID_EXISTS")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", createdSubjectId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_RUNS_ON_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.branchKind").doesNotExist())
                .andExpect(jsonPath("$.data.planId").doesNotExist())
                .andReturn();
        JsonNode data = objectMapper.readTree(got.getResponse().getContentAsString()).path("data");
        assertThat(data.path("status").asText(), is("OPEN"));
        JsonNode createAfter = itemByKind(sortedItems(data.path("items")), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode runsOnAfter = itemByKind(sortedItems(data.path("items")), "CURATED_RUNS_ON_INSERT");
        assertThat(createAfter.path("status").asText(), is("ACCEPTED"));
        assertThat(runsOnAfter.path("status").asText(), is("REJECTED"));
        assertThat(createAfter.path("subjectId").asText(), is(createdSubjectId));
        assertThat(createdSubjectId, not(is("u03a-rt-unknown")));
        assertThat(createdSubjectId, not(is("u03a-never")));
    }

    @Test
    void acceptingRunsOnBeforeCreateFailsAndLeavesCuratedUnchanged() throws Exception {
        String hostId = createHost("u03b-h");
        heartbeatUnknown(hostId, "u03b-ag", "u03b-rt-unknown", "u03b-unknown", "u03b-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u03b-rt-unknown");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode runsOnItem = itemByKind(draft.items(), "CURATED_RUNS_ON_INSERT");
        String createItemId = createItem.path("id").asText();
        String runsOnItemId = runsOnItem.path("id").asText();

        postUnboundItem(draft.draftId(), runsOnItemId, "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_RUNS_ON_BEFORE_CREATE")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + createItemId + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + runsOnItemId + "')].status",
                        hasItem("PENDING")));

        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"u03b-probe\",\"objectId\":\"u03b-never\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void acceptingCreateFailsWhenImmutableObjectIdAlreadyExists() throws Exception {
        String hostId = createHost("u03c-h");
        heartbeatUnknown(hostId, "u03c-ag", "u03c-rt-unknown", "u03c-unknown", "u03c-never");
        createContainer("u03c-existing", "u03c-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u03c-rt-unknown");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        String createItemId = createItem.path("id").asText();
        assertThat(createItem.path("payload").path("immutableObjectId").asText(), is("u03c-never"));

        postUnboundItem(draft.draftId(), createItemId, "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_OBJECT_ID_EXISTS")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        JsonNode createAfter = itemByKind(
                sortedItems(objectMapper.readTree(got.getResponse().getContentAsString()).path("data").path("items")),
                "CREATE_CONTAINER_FROM_UNBOUND");
        assertThat(createAfter.path("id").asText(), is(createItemId));
        assertThat(createAfter.path("status").asText(), is("PENDING"));
        assertThat(createAfter.path("subjectId").isNull() || createAfter.path("subjectId").isMissingNode(), is(true));

        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"u03c-probe\",\"objectId\":\"u03c-never\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_OBJECT_ID_EXISTS")));
    }

    private ResultActions postUnboundItem(String draftId, String itemId, String action) throws Exception {
        return mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/{action}",
                        draftId, itemId, action)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON));
    }

    private void heartbeatUnknown(
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

    private OpenUnboundDraft openDraftFromRuntime(String runtimeId) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode candidate = unboundByRuntimeId(listed, runtimeId);
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
                .filter(n -> kind.equals(n.path("kind").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing item kind " + kind));
    }

    private List<JsonNode> sortedItems(JsonNode itemsNode) {
        List<JsonNode> items = new ArrayList<>();
        itemsNode.forEach(items::add);
        items.sort(Comparator.comparingInt(n -> n.path("seq").asInt()));
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

    private record OpenUnboundDraft(String draftId, List<JsonNode> items) {
    }
}
