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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Change-curated ticket 05 HTTP acceptance: one behavior per method.
 * Cycle 1: pending 草案 + snapshot B→C → same-merge-key 升级, open 草案 gone, 策展 stays A.
 */
@HttpAcceptanceTest
class ChangeCuratedDraftVoidHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsOpenDraftWithoutWritingCurated()
            throws Exception {
        OpenDraft draft = openChangeCuratedDraft(
                "ccd05-up-a", "ccd05-up-b", "ctr-ccd05-up-x", "ctr-ccd05-up-y");
        String hostC = snapshotXOnHostC(draft, "ccd05-up-c");

        mockMvc.perform(get("/api/conflicts/{id}", draft.fx().conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(draft.fx().conflictId())))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostC)))
                .andExpect(jsonPath("$.data.observedLineage", hasSize(2)))
                .andExpect(jsonPath("$.data.observedLineage[0].hostId", is(draft.fx().hostB())))
                .andExpect(jsonPath("$.data.observedLineage[1].hostId", is(hostC)));

        assertEquals(1, countActiveForSubject(draft.fx().containerX()));

        getShouldWhere(draft.fx().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));
        getShouldWhere(draft.fx().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));

        getOpenDraft(draft.fx().conflictId(), GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void acceptAndRejectAfterUpgradeAreDraftVoidedAndCuratedStaysA() throws Exception {
        OpenDraft draft = openChangeCuratedDraft(
                "ccd05-acc-a", "ccd05-acc-b", "ctr-ccd05-acc-x", "ctr-ccd05-acc-y");
        snapshotXOnHostC(draft, "ccd05-acc-c");

        postItemAction(draft.fx().conflictId(), draft.itemXId(), "accept", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("DRAFT_VOIDED")))
                .andExpect(jsonPath("$.data", nullValue()));
        postItemAction(draft.fx().conflictId(), draft.itemYId(), "reject", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("DRAFT_VOIDED")))
                .andExpect(jsonPath("$.data", nullValue()));

        getShouldWhere(draft.fx().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));
        getShouldWhere(draft.fx().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.fx().hostA())));
    }

    @Test
    void getDraftByIdAfterUpgradeShowsVoidedWithPendingItems() throws Exception {
        OpenDraft draft = openChangeCuratedDraft(
                "ccd05-gid-a", "ccd05-gid-b", "ctr-ccd05-gid-x", "ctr-ccd05-gid-y");
        snapshotXOnHostC(draft, "ccd05-gid-c");

        getDraftById(draft.fx().conflictId(), draft.draftId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(draft.draftId())))
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemXId() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemYId() + "')].status",
                        hasItem("PENDING")));
    }

    private String snapshotXOnHostC(OpenDraft draft, String hostCName) throws Exception {
        String hostC = createHost(hostCName);
        heartbeatWithContainer(hostC, "agent-" + draft.fx().objectX() + "-c", draft.fx().objectX());
        return hostC;
    }

    private OpenDraft openChangeCuratedDraft(
            String hostAName, String hostBName, String objectX, String objectY
    ) throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim(hostAName, hostBName, objectX, objectY);
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isOk());
        JsonNode data = readOpenDraft(fx.conflictId());
        JsonNode items = data.path("items");
        return new OpenDraft(
                fx,
                data.path("id").asText(),
                itemId(items, fx.containerX()),
                itemId(items, fx.containerY()));
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

    private ResultActions getDraftById(String conflictId, String draftId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{conflictId}/curated-drafts/{draftId}",
                conflictId, draftId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getShouldWhere(String containerId) throws Exception {
        return mockMvc.perform(get("/api/curated/asks/should-where")
                .param("containerId", containerId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private JsonNode readOpenDraft(String conflictId) throws Exception {
        MvcResult result = getOpenDraft(conflictId, GENERAL_ID)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String itemId(JsonNode items, String subjectId) {
        for (JsonNode item : items) {
            if (subjectId.equals(item.path("subjectId").asText())) {
                return item.path("id").asText();
            }
        }
        throw new AssertionError("No 草案 item for subject " + subjectId);
    }

    private int countActiveForSubject(String subjectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/conflicts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        int count = 0;
        for (JsonNode node : data) {
            if (subjectId.equals(node.path("mergeKey").path("subjectId").asText())) {
                count++;
            }
        }
        return count;
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
        return new Fixture(conflictId, hostA, hostB, containerX, containerY, objectX);
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
            String containerY,
            String objectX
    ) {
    }

    private record OpenDraft(Fixture fx, String draftId, String itemXId, String itemYId) {
    }
}
