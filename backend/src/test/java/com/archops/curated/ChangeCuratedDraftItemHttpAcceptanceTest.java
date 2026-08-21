package com.archops.curated;

import com.archops.conflict.ConflictDiagnosisWait;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Change-curated ticket 04 HTTP acceptance: one behavior per method.
 * Cycle 1: non-handler cannot accept or reject 草案 items; 策展 stays A.
 */
@HttpAcceptanceTest
class ChangeCuratedDraftItemHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void nonHandlerCannotAcceptDraftItem() throws Exception {
        OpenDraft draft = openChangeCuratedDraft("ccd04-nh-a", "ccd04-nh-b", "ctr-ccd04-nh-x", "ctr-ccd04-nh-y");

        postItemAction(draft.fx().conflictId(), draft.itemXId(), "accept", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")))
                .andExpect(jsonPath("$.data", nullValue()));

        getShouldWhere(draft.fx().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));
        getShouldWhere(draft.fx().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));

        getOpenDraft(draft.fx().conflictId(), GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + draft.fx().containerX() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + draft.fx().containerY() + "')].status",
                        hasItem("PENDING")));
    }

    @Test
    void acceptedHandlerRejectsSiblingDoesNotWriteCurated() throws Exception {
        OpenDraft draft = openChangeCuratedDraft("ccd04-rj-a", "ccd04-rj-b", "ctr-ccd04-rj-x", "ctr-ccd04-rj-y");

        postItemAction(draft.fx().conflictId(), draft.itemYId(), "reject", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemYId() + "')].status",
                        hasItem("REJECTED")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemXId() + "')].status",
                        hasItem("PENDING")));

        getShouldWhere(draft.fx().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));
        getShouldWhere(draft.fx().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));

        mockMvc.perform(get("/api/conflicts/{id}", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));
    }

    @Test
    void acceptedHandlerAcceptsMergeKeyWritesCuratedShouldWhereToObservedHost() throws Exception {
        OpenDraft draft = openChangeCuratedDraft("ccd04-ac-a", "ccd04-ac-b", "ctr-ccd04-ac-x", "ctr-ccd04-ac-y");
        postItemAction(draft.fx().conflictId(), draft.itemYId(), "reject", GENERAL_ID)
                .andExpect(status().isOk());

        postItemAction(draft.fx().conflictId(), draft.itemXId(), "accept", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemXId() + "')].status",
                        hasItem("ACCEPTED")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemYId() + "')].status",
                        hasItem("REJECTED")));

        getShouldWhere(draft.fx().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostB())));
        getShouldWhere(draft.fx().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));

        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerId\":\"" + draft.fx().containerX()
                                + "\",\"hostId\":\"" + draft.fx().hostA() + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CURATED_RUNS_ON_EXISTS")));
    }

    @Test
    void acceptMergeKeyComparesImmediatelyToPendingCloseWithoutNewSnapshot() throws Exception {
        OpenDraft draft = rejectSiblingThenAcceptMergeKey(
                "ccd04-pc-a", "ccd04-pc-b", "ctr-ccd04-pc-x", "ctr-ccd04-pc-y");
        mockMvc.perform(get("/api/conflicts/{id}", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.status", not("CLOSED")))
                .andExpect(jsonPath("$.data.status", not("OPEN")))
                .andExpect(jsonPath("$.data.pendingCloseReminderVisible", is(true)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostB())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(draft.fx().hostB())));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", draft.fx().containerX())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.track", is("OBSERVED")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(draft.fx().hostB())))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostB())));

        mockMvc.perform(get("/api/conflicts/{id}/events", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("PENDING_CLOSE")));
    }

    @Test
    void acceptedHandlerConfirmCloseAfterDraftAcceptClosesConflict() throws Exception {
        OpenDraft draft = rejectSiblingThenAcceptMergeKey(
                "ccd04-cc-a", "ccd04-cc-b", "ctr-ccd04-cc-x", "ctr-ccd04-cc-y");
        mockMvc.perform(get("/api/conflicts/{id}", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
    }

    private OpenDraft rejectSiblingThenAcceptMergeKey(
            String hostAName, String hostBName, String objectX, String objectY
    ) throws Exception {
        OpenDraft draft = openChangeCuratedDraft(hostAName, hostBName, objectX, objectY);
        postItemAction(draft.fx().conflictId(), draft.itemYId(), "reject", GENERAL_ID)
                .andExpect(status().isOk());
        postItemAction(draft.fx().conflictId(), draft.itemXId(), "accept", GENERAL_ID)
                .andExpect(status().isOk());
        return draft;
    }

    private OpenDraft openChangeCuratedDraft(
            String hostAName, String hostBName, String objectX, String objectY
    ) throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim(hostAName, hostBName, objectX, objectY);
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isOk());
        JsonNode items = readOpenDraftItems(fx.conflictId());
        return new OpenDraft(fx, itemId(items, fx.containerX()), itemId(items, fx.containerY()));
    }

    private ResultActions postItemAction(String conflictId, String itemId, String action, String userId)
            throws Exception {
        return mockMvc.perform(post(
                "/api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/{action}",
                conflictId, itemId, action)
                .header(TempAuthHeaders.USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions postBranch(String conflictId, String userId, String forkId) throws Exception {
        return mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                .header(TempAuthHeaders.USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forkId\":\"" + forkId + "\"}")
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getOpenDraft(String conflictId, String userId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                .header(TempAuthHeaders.USER_ID, userId)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getShouldWhere(String containerId) throws Exception {
        return mockMvc.perform(get("/api/curated/asks/should-where")
                .param("containerId", containerId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private JsonNode readOpenDraftItems(String conflictId) throws Exception {
        MvcResult result = getOpenDraft(conflictId, GENERAL_ID)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private static String itemId(JsonNode items, String subjectId) {
        for (JsonNode item : items) {
            if (subjectId.equals(item.path("subjectId").asText())) {
                return item.path("id").asText();
            }
        }
        throw new AssertionError("No 草案 item for subject " + subjectId);
    }

    private Fixture openConflictWithSiblingAndClaim(
            String hostAName, String hostBName, String objectX, String objectY
    ) throws Exception {
        Fixture fx = openConflictWithSibling(hostAName, hostBName, objectX, objectY);
        mockMvc.perform(post("/api/conflicts/{id}/claim", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")));
        return fx;
    }

    private Fixture openConflictWithSibling(
            String hostAName, String hostBName, String objectX, String objectY
    ) throws Exception {
        String hostA = createHost(hostAName);
        String hostB = createHost(hostBName);
        String containerX = createContainer("app-" + objectX, objectX);
        String containerY = createContainer("app-" + objectY, objectY);
        confirmRunsOn(containerX, hostA);
        confirmRunsOn(containerY, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectX, objectX);

        MvcResult result = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String conflictId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        return new Fixture(conflictId, hostA, hostB, containerX, containerY);
    }

    private void heartbeatWithContainer(String hostId, String agentId, String objectId) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"docker-x",
                                      "name":"app",
                                      "labels":{"archops.object_id":"%s"}
                                    }]
                                  }
                                }
                                """.formatted(agentId, hostId, objectId))
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
        return readDataId(result);
    }

    private String createContainer(String name, String objectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"objectId\":\"" + objectId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return readDataId(result);
    }

    private void confirmRunsOn(String containerId, String hostId) throws Exception {
        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerId\":\"" + containerId + "\",\"hostId\":\"" + hostId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private String readDataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private record Fixture(
            String conflictId,
            String hostA,
            String hostB,
            String containerX,
            String containerY
    ) {
    }

    private record OpenDraft(Fixture fx, String itemXId, String itemYId) {
    }
}
