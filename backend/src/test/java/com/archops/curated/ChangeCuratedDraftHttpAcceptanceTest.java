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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Change-curated ticket 03 HTTP acceptance: one behavior per method.
 * Cycle 1: accepted handler selects 改理想 → exactly one open 草案 with ≥2 pending 运行于 items.
 */
@HttpAcceptanceTest
class ChangeCuratedDraftHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void acceptedHandlerSelectsChangeCuratedOpensDraftWithTwoPendingRunsOnItems() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-a", "ccd-b", "ctr-ccd-x", "ctr-ccd-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.selectedForkId", is("CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(2))));

        getOpenDraft(fx.conflictId(), GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].fromHostId",
                        hasItem(fx.hostA())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].toHostId",
                        hasItem(fx.hostB())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].fromHostId",
                        hasItem(fx.hostA())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].toHostId",
                        hasItem(fx.hostB())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].kind",
                        hasItem("RUNS_ON_TARGET_CHANGE")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].kind",
                        hasItem("RUNS_ON_TARGET_CHANGE")));
    }

    @Test
    void selectChangeCuratedDoesNotWriteCuratedShouldWhere() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-sw-a", "ccd-sw-b", "ctr-ccd-sw-x", "ctr-ccd-sw-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk());

        getShouldWhere(fx.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())));
        getShouldWhere(fx.containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())));
    }

    @Test
    void selectChangeCuratedDoesNotCreateActiveOperationPlan() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-np-a", "ccd-np-b", "ctr-ccd-np-x", "ctr-ccd-np-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));
    }

    @Test
    void nonHandlerCannotSelectChangeCurated() throws Exception {
        Fixture claimed = openConflictWithSiblingAndClaim("ccd-nh-a", "ccd-nh-b", "ctr-ccd-nh-x", "ctr-ccd-nh-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, claimed.conflictId(), GENERAL_ID);
        postBranch(claimed.conflictId(), SENIOR_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
    }

    @Test
    void pendingHandlerCannotSelectChangeCurated() throws Exception {
        Fixture pending = openConflictWithSibling("ccd-pe-a", "ccd-pe-b", "ctr-ccd-pe-x", "ctr-ccd-pe-y");
        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", pending.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("NONE")));
        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", pending.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + GENERAL_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")));
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, pending.conflictId(), GENERAL_ID);
        postBranch(pending.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
    }

    @Test
    void staleDiagnosisCannotSelectChangeCurated() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-st-a", "ccd-st-b", "ctr-ccd-st-x", "ctr-ccd-st-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        String staleDiagnosisId = readDiagnosisId(fx.conflictId());

        String hostC = createHost("ccd-st-c");
        heartbeatWithContainer(hostC, "agent-ccd-st-c", "ctr-ccd-st-x");
        waitUntilDiagnosisReplaced(fx.conflictId(), staleDiagnosisId);

        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", staleDiagnosisId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DIAGNOSIS_NOT_READY")));
    }

    @Test
    void openDraftRejectsSecondChangeCuratedSelection() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-od-a", "ccd-od-b", "ctr-ccd-od-x", "ctr-ccd-od-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));

        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_ALREADY_OPEN")));
    }

    @Test
    void openDraftBlocksFixActualSelection() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-bf-a", "ccd-bf-b", "ctr-ccd-bf-x", "ctr-ccd-bf-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk());

        postBranch(fx.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("OPEN_DRAFT_BLOCKS_FIX_ACTUAL")));
    }

    @Test
    void fixActualStillSkipsDraftAndCreatesOperationPlan() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-fa-a", "ccd-fa-b", "ctr-ccd-fa-x", "ctr-ccd-fa-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        postBranch(fx.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.selectedForkId", is("FIX_ACTUAL_TO_CURATED")));

        getOpenDraft(fx.conflictId(), GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));

        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)));
    }

    private org.springframework.test.web.servlet.ResultActions postBranch(
            String conflictId, String userId, String forkId, String diagnosisId
    ) throws Exception {
        String body = diagnosisId == null
                ? "{\"forkId\":\"" + forkId + "\"}"
                : "{\"forkId\":\"" + forkId + "\",\"diagnosisId\":\"" + diagnosisId + "\"}";
        return mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                .header(TempAuthHeaders.USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .accept(MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.servlet.ResultActions getOpenDraft(String conflictId, String userId)
            throws Exception {
        return mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                .header(TempAuthHeaders.USER_ID, userId)
                .accept(MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.servlet.ResultActions getShouldWhere(String containerId) throws Exception {
        return mockMvc.perform(get("/api/curated/asks/should-where")
                .param("containerId", containerId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private String readDiagnosisId(String conflictId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private void waitUntilDiagnosisReplaced(String conflictId, String previousId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                            .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
                String status = data.path("status").asText();
                String id = data.path("id").asText();
                if ("READY".equals(status) && !previousId.equals(id)) {
                    return;
                }
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Timed out waiting for replacement READY diagnosis on " + conflictId);
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
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }

    private record Fixture(
            String conflictId,
            String hostA,
            String hostB,
            String containerX,
            String containerY
    ) {
    }
}
