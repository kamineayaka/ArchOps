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
    private static final String SENIOR_ID = "user-senior-demo";

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

    @Test
    void acceptingBindToIdentityLostLeavesPrimaryKeyAndDoesNotWriteObservedRunsOn() throws Exception {
        String hostId = createHost("u03d-h");
        String containerX = createContainer("u03d-x", "u03d-oid");
        confirmRunsOn(containerX, hostId);
        heartbeatMissingLabel(hostId, "u03d-ag", "u03d-rt-miss", "u03d-similar");

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        OpenUnboundDraft draft = openDraftFromRuntime("u03d-rt-miss");
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
                .andExpect(jsonPath("$.data.subject.objectId", is("u03d-oid")))
                .andExpect(jsonPath("$.data.target.id", is(hostId)));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u03d-rt-miss"), nullValue());

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));
    }

    @Test
    void unlabeledReheartbeatAfterBindStaysConsumedAndIdentityLost() throws Exception {
        String hostId = createHost("u03e-h");
        String containerX = createContainer("u03e-x", "u03e-oid");
        confirmRunsOn(containerX, hostId);
        heartbeatMissingLabel(hostId, "u03e-ag", "u03e-rt-miss", "u03e-similar");
        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        OpenUnboundDraft draft = openDraftFromRuntime("u03e-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        postUnboundItem(draft.draftId(), bindItem.path("id").asText(), "accept")
                .andExpect(status().isOk());

        heartbeatMissingLabel(hostId, "u03e-ag", "u03e-rt-miss", "u03e-renamed");

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u03e-rt-miss"), nullValue());

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")));
        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));
        mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));
    }

    @Test
    void acceptingCreateAfterBindFailsAsCandidateConsumed() throws Exception {
        String hostId = createHost("u03f-h");
        String containerX = createContainer("u03f-x", "u03f-oid");
        confirmRunsOn(containerX, hostId);
        heartbeatMissingLabel(hostId, "u03f-ag", "u03f-rt-miss", "u03f-similar");
        OpenUnboundDraft draft = openDraftFromRuntime("u03f-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        postUnboundItem(draft.draftId(), bindItem.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));

        postUnboundItem(draft.draftId(), createItem.path("id").asText(), "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_CANDIDATE_CONSUMED")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createAfter = itemByKind(
                sortedItems(objectMapper.readTree(got.getResponse().getContentAsString()).path("data").path("items")),
                "CREATE_CONTAINER_FROM_UNBOUND");
        assertThat(createAfter.path("status").asText(), is("PENDING"));
        assertThat(createAfter.path("status").asText(), not(is("ACCEPTED")));
    }

    @Test
    void bindingToLabelMatchedPresentTargetIsRejected() throws Exception {
        String hostId = createHost("u03g-h");
        String containerX = createContainer("u03g-x", "u03g-oid");
        confirmRunsOn(containerX, hostId);
        heartbeatMissingLabel(hostId, "u03g-ag", "u03g-rt-miss", "u03g-similar");
        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        OpenUnboundDraft draft = openDraftFromRuntime("u03g-rt-miss");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        String bindItemId = bindItem.path("id").asText();

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u03g-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u03g-rt-hit",
                                      "name": "u03g-x",
                                      "labels": { "archops.object_id": "u03g-oid" }
                                    }]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched[0].curatedContainerId", is(containerX)))
                .andExpect(jsonPath("$.data.matched[0].observedHostId", is(hostId)));

        postUnboundItem(draft.draftId(), bindItemId, "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_BIND_TARGET_HEALTHY")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bindAfter = itemByKind(
                sortedItems(objectMapper.readTree(got.getResponse().getContentAsString()).path("data").path("items")),
                "BIND_UNBOUND_TO_EXISTING");
        assertThat(bindAfter.path("id").asText(), is(bindItemId));
        assertThat(bindAfter.path("status").asText(), is("PENDING"));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject.objectId", is("u03g-oid")))
                .andExpect(jsonPath("$.data.subject.id", is(containerX)));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void missingLabelCreateAcceptIsNotASuccessPath() throws Exception {
        String hostId = createHost("u03h-h");
        String containerX = createContainer("u03h-x", "u03h-oid");
        confirmRunsOn(containerX, hostId);
        heartbeatMissingLabel(hostId, "u03h-ag", "u03h-rt-miss", "u03h-similar");
        OpenUnboundDraft draft = openDraftFromRuntime("u03h-rt-miss");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        String createItemId = createItem.path("id").asText();

        postUnboundItem(draft.draftId(), createItemId, "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_CREATE_IMMUTABLE_ID_MISSING")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createAfter = itemByKind(
                sortedItems(objectMapper.readTree(got.getResponse().getContentAsString()).path("data").path("items")),
                "CREATE_CONTAINER_FROM_UNBOUND");
        assertThat(createAfter.path("status").asText(), is("PENDING"));

        postUnboundItem(draft.draftId(), createItemId, "reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + createItemId + "')].status",
                        hasItem("REJECTED")));
    }

    @Test
    void unknownBindToExistingDoesNotRewriteWrongLabelAsPrimaryKey() throws Exception {
        String hostId = createHost("u03i-h");
        String containerX = createContainer("u03i-x", "u03i-oid");
        confirmRunsOn(containerX, hostId);
        heartbeatUnknown(hostId, "u03i-ag", "u03i-rt-unknown", "u03i-unknown", "u03i-wrong");
        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        OpenUnboundDraft draft = openDraftFromRuntime("u03i-rt-unknown");
        JsonNode bindItem = itemByKind(draft.items(), "BIND_UNBOUND_TO_EXISTING");
        assertThat(bindItem.path("subjectId").asText(), is(containerX));

        postUnboundItem(draft.draftId(), bindItem.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject.id", is(containerX)))
                .andExpect(jsonPath("$.data.subject.objectId", is("u03i-oid")));

        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"u03i-probe\",\"objectId\":\"u03i-wrong\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listed, "u03i-rt-unknown"), nullValue());

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")));

        String host2 = createHost("u03i2-h");
        String containerX2 = createContainer("u03i2-x", "u03i2-oid");
        confirmRunsOn(containerX2, host2);
        heartbeatUnknown(host2, "u03i2-ag", "u03i2-rt-unknown", "u03i2-unknown", "u03i2-wrong");
        OpenUnboundDraft draft2 = openDraftFromRuntime("u03i2-rt-unknown");
        JsonNode create2 = itemByKind(draft2.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode bind2 = itemByKind(draft2.items(), "BIND_UNBOUND_TO_EXISTING");
        postUnboundItem(draft2.draftId(), create2.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='CREATE_CONTAINER_FROM_UNBOUND')].status",
                        hasItem("ACCEPTED")));
        postUnboundItem(draft2.draftId(), bind2.path("id").asText(), "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("UNBOUND_CANDIDATE_CONSUMED")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void unauthenticatedItemAcceptIsRejected() throws Exception {
        String hostId = createHost("u03j-h");
        heartbeatUnknown(hostId, "u03j-ag", "u03j-rt-unknown", "u03j-unknown", "u03j-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u03j-rt-unknown");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");

        mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/accept",
                        draft.draftId(), createItem.path("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void seniorOperatorCanAcceptCreateOnUnboundDraft() throws Exception {
        String hostId = createHost("u03js-h");
        heartbeatUnknown(hostId, "u03js-ag", "u03js-rt-unknown", "u03js-unknown", "u03js-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u03js-rt-unknown");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/accept",
                        draft.draftId(), createItem.path("id").asText())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.kind=='CREATE_CONTAINER_FROM_UNBOUND')].status",
                        hasItem("ACCEPTED")));
    }

    @Test
    void bootstrapFirstRunsOnStillInsertsAndOverwriteStillRejected() throws Exception {
        String hostId = createHost("u03k-h");
        String containerId = createContainer("u03k-x", "u03k-oid");
        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerId\":\"" + containerId + "\",\"hostId\":\"" + hostId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.relationLabel", is("运行于")))
                .andExpect(jsonPath("$.data.target.id", is(hostId)));
        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerId\":\"" + containerId + "\",\"hostId\":\"" + hostId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_RUNS_ON_EXISTS")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void itemReviewEventsAreReadableAndWholeDraftAcceptDoesNotExist() throws Exception {
        String hostId = createHost("u03l-h");
        heartbeatUnknown(hostId, "u03l-ag", "u03l-rt-unknown", "u03l-unknown", "u03l-never");
        OpenUnboundDraft draft = openDraftFromRuntime("u03l-rt-unknown");
        JsonNode createItem = itemByKind(draft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode runsOnItem = itemByKind(draft.items(), "CURATED_RUNS_ON_INSERT");
        String createItemId = createItem.path("id").asText();
        String runsOnItemId = runsOnItem.path("id").asText();

        postUnboundItem(draft.draftId(), createItemId, "accept")
                .andExpect(status().isOk());
        postUnboundItem(draft.draftId(), runsOnItemId, "reject")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/curated-drafts/{draftId}/events", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("DRAFT_CREATED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("DRAFT_ITEM_ACCEPTED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("DRAFT_ITEM_REJECTED")))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_ACCEPTED')].detail.hint",
                        hasItem(containsString("草案条目已接受"))))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_REJECTED')].detail.hint",
                        hasItem(containsString("草案条目已拒绝"))))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_ACCEPTED')].actorUserId",
                        hasItem(GENERAL_ID)))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_REJECTED')].actorUserId",
                        hasItem(GENERAL_ID)))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_ACCEPTED')].detail.draftId",
                        hasItem(draft.draftId())))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_ACCEPTED')].detail.itemId",
                        hasItem(createItemId)))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_REJECTED')].detail.itemId",
                        hasItem(runsOnItemId)));

        mockMvc.perform(post("/api/curated-drafts/{draftId}/accept", draft.draftId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)));

        mockMvc.perform(get("/api/conflicts/{conflictId}/operation-plans/active", "u03l-none")
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
