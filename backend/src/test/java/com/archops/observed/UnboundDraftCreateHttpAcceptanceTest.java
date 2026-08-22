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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * Ticket 02 HTTP acceptance: open a 未绑定草案 from one 待并入 candidate (no conflict).
 * One behavior per method; witnessed red → green → refactor.
 */
@HttpAcceptanceTest
class UnboundDraftCreateHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generalOperatorOpensDraftFromUnknownObjectIdCandidate() throws Exception {
        String hostId = createHost("u02a-h");

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02a-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u02a-rt-unknown",
                                        "name": "u02a-unknown",
                                        "labels": { "archops.object_id": "u02a-never" }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode candidate = unboundByRuntimeId(listed, "u02a-rt-unknown");
        assertThat(candidate, notNullValue());
        String candidateId = candidate.path("id").asText();

        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.origin", is("UNBOUND_CANDIDATE")))
                .andExpect(jsonPath("$.data.conflictId").value(nullValue()))
                .andExpect(jsonPath("$.data.diagnosisId").value(nullValue()))
                .andExpect(jsonPath("$.data.selectedForkId").value(nullValue()))
                .andExpect(jsonPath("$.data.candidateId", is(candidateId)))
                .andExpect(jsonPath("$.data.sourceHostId", is(hostId)))
                .andExpect(jsonPath("$.data.runtimeId", is("u02a-rt-unknown")))
                .andExpect(jsonPath("$.data.createdBy", is(GENERAL_ID)))
                .andExpect(jsonPath("$.data.branchKind").doesNotExist())
                .andExpect(jsonPath("$.data.skipsDraft").doesNotExist())
                .andExpect(jsonPath("$.data.planId").doesNotExist())
                .andReturn();

        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        List<JsonNode> items = sortedItems(data.path("items"));
        assertThat(items.size(), is(2));
        assertThat(items.stream().map(n -> n.path("kind").asText()).toList(),
                containsInAnyOrder("CREATE_CONTAINER_FROM_UNBOUND", "CURATED_RUNS_ON_INSERT"));
        assertThat(items.stream().map(n -> n.path("status").asText()).distinct().toList(),
                is(List.of("PENDING")));
        assertThat(items.stream().noneMatch(n -> "RUNS_ON_TARGET_CHANGE".equals(n.path("kind").asText())), is(true));
        assertThat(items.stream().noneMatch(n -> "BIND_UNBOUND_TO_EXISTING".equals(n.path("kind").asText())), is(true));

        JsonNode create = items.stream()
                .filter(n -> "CREATE_CONTAINER_FROM_UNBOUND".equals(n.path("kind").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(create.path("subjectId").isNull() || create.path("subjectId").isMissingNode(), is(true));
        assertThat(create.path("payload").path("immutableObjectId").asText(), is("u02a-never"));
        assertThat(create.path("payload").path("labels").path("archops.object_id").asText(), is("u02a-never"));
        assertThat(create.path("payload").path("proposedName").asText(), is("u02a-unknown"));

        JsonNode runsOn = items.stream()
                .filter(n -> "CURATED_RUNS_ON_INSERT".equals(n.path("kind").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(runsOn.path("subjectId").isNull() || runsOn.path("subjectId").isMissingNode(), is(true));
        assertThat(runsOn.path("toHostId").asText(), is(hostId));
    }


    @Test
    void getCuratedDraftByIdReadsOpenUnboundDraft() throws Exception {
        String hostId = createHost("u02b-h");

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02b-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u02b-rt-unknown",
                                        "name": "u02b-unknown",
                                        "labels": { "archops.object_id": "u02b-never" }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode candidate = unboundByRuntimeId(listed, "u02b-rt-unknown");
        assertThat(candidate, notNullValue());
        String candidateId = candidate.path("id").asText();

        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        String draftId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        MvcResult got = mockMvc.perform(get("/api/curated-drafts/{draftId}", draftId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(draftId)))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.origin", is("UNBOUND_CANDIDATE")))
                .andExpect(jsonPath("$.data.conflictId").value(nullValue()))
                .andExpect(jsonPath("$.data.diagnosisId").value(nullValue()))
                .andExpect(jsonPath("$.data.selectedForkId").value(nullValue()))
                .andExpect(jsonPath("$.data.candidateId", is(candidateId)))
                .andExpect(jsonPath("$.data.sourceHostId", is(hostId)))
                .andExpect(jsonPath("$.data.runtimeId", is("u02b-rt-unknown")))
                .andExpect(jsonPath("$.data.createdBy", is(GENERAL_ID)))
                .andReturn();

        JsonNode data = objectMapper.readTree(got.getResponse().getContentAsString()).path("data");
        List<JsonNode> items = sortedItems(data.path("items"));
        assertThat(items.size(), is(2));
        assertThat(items.stream().map(n -> n.path("kind").asText()).toList(),
                containsInAnyOrder("CREATE_CONTAINER_FROM_UNBOUND", "CURATED_RUNS_ON_INSERT"));
        assertThat(items.stream().map(n -> n.path("status").asText()).distinct().toList(),
                is(List.of("PENDING")));
    }


    @Test
    void openingDraftDoesNotWriteCuratedOrConsumeCandidate() throws Exception {
        String hostId = createHost("u02c-h");
        String containerX = createContainer("u02c-x", "u02c-oid");
        confirmRunsOn(containerX, hostId);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02c-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u02c-rt-unknown",
                                        "name": "u02c-unknown",
                                        "labels": { "archops.object_id": "u02c-never" }
                                      }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode candidate = unboundByRuntimeId(listed, "u02c-rt-unknown");
        assertThat(candidate, notNullValue());

        mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidate.path("id").asText())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostId)));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.id", is(hostId)));

        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"u02c-probe\",\"objectId\":\"u02c-never\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        MvcResult listedAfter = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(unboundByRuntimeId(listedAfter, "u02c-rt-unknown"), notNullValue());
    }


    @Test
    void unauthenticatedOpenDraftIsRejected() throws Exception {
        String hostId = createHost("u02d-h");
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02d-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02d-rt-unknown",
                                      "name": "u02d-unknown",
                                      "labels": { "archops.object_id": "u02d-never" }
                                    }]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String candidateId = unboundByRuntimeId(listed, "u02d-rt-unknown").path("id").asText();

        mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void seniorOperatorCanOpenDraftFromUnknownCandidate() throws Exception {
        String hostId = createHost("u02e-h");
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02e-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02e-rt-unknown",
                                      "name": "u02e-unknown",
                                      "labels": { "archops.object_id": "u02e-never" }
                                    }]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String candidateId = unboundByRuntimeId(listed, "u02e-rt-unknown").path("id").asText();

        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.createdBy", is(SENIOR_ID)))
                .andReturn();
        List<JsonNode> items = sortedItems(objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("items"));
        assertThat(items.size() >= 2, is(true));
        assertThat(items.stream().map(n -> n.path("status").asText()).distinct().toList(), is(List.of("PENDING")));
    }

    @Test
    void identityLostMissingLabelDraftOffersBindVersusCreate() throws Exception {
        String hostId = createHost("u02f-h");
        String containerX = createContainer("u02f-x", "u02f-oid");
        confirmRunsOn(containerX, hostId);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02f-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02f-rt-miss",
                                      "name": "u02f-similar",
                                      "labels": {}
                                    }],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode candidate = unboundByRuntimeId(listed, "u02f-rt-miss");
        assertThat(candidate, notNullValue());
        assertThat(candidate.path("reason").asText(), is("MISSING_LABEL"));

        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidate.path("id").asText())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();

        List<JsonNode> items = sortedItems(objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("items"));
        assertThat(items.size(), is(2));
        assertThat(items.stream().map(n -> n.path("kind").asText()).toList(),
                containsInAnyOrder("BIND_UNBOUND_TO_EXISTING", "CREATE_CONTAINER_FROM_UNBOUND"));
        assertThat(items.stream().noneMatch(n -> "CURATED_RUNS_ON_INSERT".equals(n.path("kind").asText())), is(true));
        JsonNode bind = items.stream().filter(n -> "BIND_UNBOUND_TO_EXISTING".equals(n.path("kind").asText())).findFirst().orElseThrow();
        assertThat(bind.path("subjectId").asText(), is(containerX));
        assertThat(bind.path("status").asText(), is("PENDING"));
        JsonNode create = items.stream().filter(n -> "CREATE_CONTAINER_FROM_UNBOUND".equals(n.path("kind").asText())).findFirst().orElseThrow();
        assertThat(create.path("subjectId").isNull() || create.path("subjectId").isMissingNode(), is(true));
        assertThat(create.path("payload").path("immutableObjectId").isNull()
                || create.path("payload").path("immutableObjectId").isMissingNode()
                || create.path("payload").path("immutableObjectId").isNull(), is(true));
        assertThat(create.path("status").asText(), is("PENDING"));

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
        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostId)));
    }

    @Test
    void secondOpenDraftForSameFieldEntityIsRejected() throws Exception {
        String hostId = createHost("u02g-h");
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02g-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02g-rt-unknown",
                                      "name": "u02g-unknown",
                                      "labels": { "archops.object_id": "u02g-never" }
                                    }]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String candidateId = unboundByRuntimeId(listed, "u02g-rt-unknown").path("id").asText();

        MvcResult first = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String draftId = objectMapper.readTree(first.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_DRAFT_ALREADY_OPEN")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/curated-drafts/{id}", draftId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.items.length()", is(2)));
    }

    @Test
    void unboundDraftIsInvisibleOnConflictCuratedDraftApis() throws Exception {
        // Unbound entity set
        String hostA1 = createHost("u02h1-h");
        String containerX = createContainer("u02h1-x", "u02h1-oid");
        confirmRunsOn(containerX, hostA1);
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02h1-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02h1-rt-miss",
                                      "name": "u02h1-similar",
                                      "labels": {}
                                    }],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostA1))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String candidateId = unboundByRuntimeId(listed, "u02h1-rt-miss").path("id").asText();
        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String unboundDraftId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();

        // Separate conflict canvas for Y
        String hostA2 = createHost("u02h2-ha");
        String hostB2 = createHost("u02h2-hb");
        String containerY = createContainer("u02h2-y", "u02h2-oid");
        confirmRunsOn(containerY, hostA2);
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02h2-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02h2-rt",
                                      "name": "u02h2-y",
                                      "labels": { "archops.object_id": "u02h2-oid" }
                                    }]
                                  }
                                }
                                """.formatted(hostB2))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult conflictResult = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerY)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String conflictId = objectMapper.readTree(conflictResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));
        mockMvc.perform(get("/api/conflicts/{cid}/curated-drafts/{did}", conflictId, unboundDraftId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));
        mockMvc.perform(get("/api/curated-drafts/{id}", unboundDraftId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conflictId").value(nullValue()))
                .andExpect(jsonPath("$.data.origin", is("UNBOUND_CANDIDATE")));
        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));
    }

    @Test
    void draftCreatedEventIsReadableOnDraftEventsApi() throws Exception {
        String hostId = createHost("u02j-h");
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u02j-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [{
                                      "runtimeId": "u02j-rt-unknown",
                                      "name": "u02j-unknown",
                                      "labels": { "archops.object_id": "u02j-never" }
                                    }]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String candidateId = unboundByRuntimeId(listed, "u02j-rt-unknown").path("id").asText();
        MvcResult created = mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", candidateId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String draftId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(get("/api/curated-drafts/{id}/events", draftId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("DRAFT_CREATED")))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_CREATED')].detail.hint",
                        hasItem(containsString("草案已创建"))))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_CREATED')].actorUserId",
                        hasItem(GENERAL_ID)))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_CREATED')].detail.draftId",
                        hasItem(draftId)))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_CREATED')].detail.origin",
                        hasItem("UNBOUND_CANDIDATE")));
    }

    @Test
    void unknownCandidateIdOpenDraftIsBusinessError() throws Exception {
        mockMvc.perform(post("/api/observed/unbound-candidates/{id}/drafts", "u02k-missing")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_CANDIDATE_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));
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
}
