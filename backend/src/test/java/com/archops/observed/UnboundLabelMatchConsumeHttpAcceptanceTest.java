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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 04 HTTP acceptance: label match clears 身份失联, consumes bind memory and
 * 未绑定候选, voids related OPEN 未绑定草案, and restores the upgrade chain.
 */
@HttpAcceptanceTest
class UnboundLabelMatchConsumeHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void labelMatchClearsIdentityLostAndFlipsActualWhere() throws Exception {
        String hostA = createHost("u04a-h");
        String containerX = createContainer("u04a-x", "u04a-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04a-ag", "u04a-rt-miss", "u04a-similar");

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        heartbeatLabeled(hostA, "u04a-ag", "u04a-rt-hit", "u04a-x", "u04a-oid");

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("IDENTITY_LOST_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.identityLost", is(false)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));
    }

    @Test
    void labelMatchConsumesBindMemorySoALaterEntityCanBindAgain() throws Exception {
        String hostA = createHost("u04b-h");
        String containerX = createContainer("u04b-x", "u04b-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04b-ag", "u04b-rt-1", "u04b-similar");
        OpenUnboundDraft first = openDraftFromRuntime("u04b-rt-1");
        JsonNode firstBind = itemByKind(first.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(firstBind.path("subjectId").asText(), is(containerX));
        postUnboundItem(first.draftId(), firstBind.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));

        heartbeatLabeled(hostA, "u04b-ag", "u04b-rt-hit", "u04b-x", "u04b-oid");

        MvcResult afterHit = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(afterHit, "u04b-rt-1"), nullValue());

        heartbeatMissingLabel(hostA, "u04b-ag", "u04b-rt-2", "u04b-similar");

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u04b-rt-2"), notNullValue());

        OpenUnboundDraft second = openDraftFromRuntime("u04b-rt-2");
        JsonNode secondBind = itemByKind(second.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(secondBind.path("subjectId").asText(), is(containerX));
        postUnboundItem(second.draftId(), secondBind.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));
    }

    @Test
    void labelMatchVoidsOpenUnboundDraftAndRejectsFurtherReview() throws Exception {
        String hostA = createHost("u04c-h");
        String containerX = createContainer("u04c-x", "u04c-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04c-ag", "u04c-rt-miss", "u04c-similar");
        OpenUnboundDraft draft = openDraftFromRuntime("u04c-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        String bindItemId = bindItem.path("id").asText();
        assertThat(bindItem.path("status").asText(), is("PENDING"));
        assertThat(createItem.path("status").asText(), is("PENDING"));

        heartbeatLabeled(hostA, "u04c-ag", "u04c-rt-hit", "u04c-x", "u04c-oid");

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andReturn();
        JsonNode items = objectMapper.readTree(got.getResponse().getContentAsString()).path("data").path("items");
        assertThat(itemByKind(sortedItems(items), "BIND_UNBOUND_TO_EXISTING").path("status").asText(), is("PENDING"));
        assertThat(itemByKind(sortedItems(items), "CREATE_CONTAINER_FROM_UNBOUND").path("status").asText(), is("PENDING"));

        postUnboundItem(draft.draftId(), bindItemId, "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("DRAFT_VOIDED")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/curated-drafts/{draftId}/events", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("DRAFT_VOIDED")))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_VOIDED')].detail.hint",
                        hasItem(containsString("草案已作废"))));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.id", is(hostA)))
                .andExpect(jsonPath("$.data.subject.objectId", is("u04c-oid")));
    }

    @Test
    void unlabeledReheartbeatDoesNotVoidOpenUnboundDraft() throws Exception {
        String hostA = createHost("u04d-h");
        String containerX = createContainer("u04d-x", "u04d-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04d-ag", "u04d-rt-miss", "u04d-similar");
        OpenUnboundDraft draft = openDraftFromRuntime("u04d-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(bindItem.path("status").asText(), is("PENDING"));

        heartbeatMissingLabel(hostA, "u04d-ag", "u04d-rt-miss", "u04d-renamed");

        mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));
        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bindAfter = itemByKind(
                sortedItems(objectMapper.readTree(got.getResponse().getContentAsString()).path("data").path("items")),
                "BIND_UNBOUND_TO_EXISTING");
        assertThat(bindAfter.path("status").asText(), is("PENDING"));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stillPending = unboundByRuntimeId(listed, "u04d-rt-miss");
        assertThat(stillPending, notNullValue());
        assertThat(stillPending.path("name").asText(), is("u04d-renamed"));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void labelMatchOnADifferentHostRestoresTheUpgradeChain() throws Exception {
        String hostA = createHost("u04e-ha");
        String hostB = createHost("u04e-hb");
        String hostC = createHost("u04e-hc");
        String containerX = createContainer("u04e-x", "u04e-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04e-ag", "u04e-rt-miss", "u04e-similar");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        heartbeatLabeled(hostB, "u04e-agb", "u04e-rt-hit", "u04e-x", "u04e-oid");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("IDENTITY_LOST_NOT_FOUND")));
        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(false)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)));

        heartbeatLabeled(hostC, "u04e-agc", "u04e-rt-hit-c", "u04e-x", "u04e-oid");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostC)))
                .andExpect(jsonPath("$.data.observedLineage[0].hostId", is(hostB)))
                .andExpect(jsonPath("$.data.observedLineage[1].hostId", is(hostC)));
    }

    @Test
    void acceptedCreateAndRunsOnThenEqualHitDoesNotInventAConflict() throws Exception {
        String hostA = createHost("u04f-h");
        heartbeatUnknown(hostA, "u04f-ag", "u04f-rt", "u04f-unknown", "u04f-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u04f-rt");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode runsOnItem = itemByKind(draft.items(), "CURATED_RUNS_ON_INSERT");
        MvcResult acceptedCreate = postUnboundItem(draft.draftId(), createItem.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andReturn();
        String subjectId = itemByKind(
                sortedItems(objectMapper.readTree(acceptedCreate.getResponse().getContentAsString())
                        .path("data").path("items")),
                "CREATE_CONTAINER_FROM_UNBOUND").path("subjectId").asText();
        postUnboundItem(draft.draftId(), runsOnItem.path("id").asText(), "accept")
                .andExpect(status().isOk());

        heartbeatLabeled(hostA, "u04f-ag", "u04f-rt", "u04f-unknown", "u04f-never");

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", subjectId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", subjectId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.identityLost", is(false)));
        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", subjectId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u04f-rt"), nullValue());
    }

    @Test
    void runtimeIdChangeIsANewCandidateAndReleasesStaleBindMemory() throws Exception {
        String hostA = createHost("u04g-h");
        String containerX = createContainer("u04g-x", "u04g-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04g-ag", "u04g-rt-1", "u04g-x");
        OpenUnboundDraft first = openDraftFromRuntime("u04g-rt-1");
        JsonNode firstBind = itemByKind(first.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(firstBind.path("subjectId").asText(), is(containerX));
        postUnboundItem(first.draftId(), firstBind.path("id").asText(), "accept")
                .andExpect(status().isOk());

        heartbeatMissingLabel(hostA, "u04g-ag", "u04g-rt-2", "u04g-x");

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u04g-rt-2"), notNullValue());
        assertThat(unboundByRuntimeId(listed, "u04g-rt-1"), nullValue());

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));
        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        OpenUnboundDraft second = openDraftFromRuntime("u04g-rt-2");
        JsonNode secondBind = itemByKind(second.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(secondBind.path("subjectId").asText(), is(containerX));
        postUnboundItem(second.draftId(), secondBind.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void absentObjectIdsAfterBindIsObservedAbsenceAndReturnsTheFieldEntityToPending() throws Exception {
        String hostA = createHost("u04h-h");
        String containerX = createContainer("u04h-x", "u04h-oid");
        confirmRunsOn(containerX, hostA);
        heartbeatMissingLabel(hostA, "u04h-ag", "u04h-rt-miss", "u04h-similar");
        OpenUnboundDraft first = openDraftFromRuntime("u04h-rt-miss");
        JsonNode firstBind = itemByKind(first.items(), "BIND_UNBOUND_TO_EXISTING");
        postUnboundItem(first.draftId(), firstBind.path("id").asText(), "accept")
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u04h-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u04h-rt-miss",
                                      "name": "u04h-similar",
                                      "labels": {}
                                    }],
                                    "absentObjectIds": ["u04h-oid"]
                                  }
                                }
                                """.formatted(hostA))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(false)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("ABSENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("IDENTITY_LOST_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u04h-rt-miss"), notNullValue());
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

    private ResultActions postUnboundItem(String draftId, String itemId, String action) throws Exception {
        return mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/{action}",
                        draftId, itemId, action)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON));
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

    private record OpenUnboundDraft(String draftId, List<JsonNode> items) {
    }
}
