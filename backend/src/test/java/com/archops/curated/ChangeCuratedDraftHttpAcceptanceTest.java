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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Change-curated HTTP acceptance: ticket 03 select-branch draft, ticket 04 itemized
 * accept (write + same-engine compare) / reject (no write).
 */
@HttpAcceptanceTest
class ChangeCuratedDraftHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String GENERAL_2_ID = "user-general-2-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void acceptedHandlerSelectsChangeCuratedOpensDraftWithoutWritingCuratedOrPlan() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-a", "ccd-b", "ctr-ccd-x", "ctr-ccd-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forks[*].id", hasItems("FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')].kind", hasItem("CHANGE_CURATED")));

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"CHANGE_CURATED_TO_OBSERVED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.selectedForkId", is("CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
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
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].kind",
                        hasItem("RUNS_ON_TARGET_CHANGE")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].kind",
                        hasItem("RUNS_ON_TARGET_CHANGE")));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", fx.containerX())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", fx.containerY())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())));

        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));

        mockMvc.perform(get("/api/conflicts/{id}/events", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("DRAFT_CREATED")))
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_CREATED')].detail.hint", hasItem("草案已创建")));
    }

    @Test
    void acceptedHandlerRejectsSiblingThenAcceptsMergeKeyWritesCuratedAndEntersPendingClose()
            throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-mx-a", "ccd-mx-b", "ctr-ccd-mx-x", "ctr-ccd-mx-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));

        JsonNode draft = readOpenDraft(fx.conflictId());
        String draftId = draft.path("id").asText();
        String itemX = itemIdForSubject(draft, fx.containerX());
        String itemY = itemIdForSubject(draft, fx.containerY());

        postItemDecision(draftId, itemX, "accept", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        assertShouldWhereHost(fx.containerX(), fx.hostA());
        assertShouldWhereHost(fx.containerY(), fx.hostA());

        postItemDecision(draftId, itemY, "reject", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].status",
                        hasItem("REJECTED")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].status",
                        hasItem("PENDING")));
        assertShouldWhereHost(fx.containerY(), fx.hostA());
        assertShouldWhereHost(fx.containerX(), fx.hostA());

        postItemDecision(draftId, itemX, "accept", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].status",
                        hasItem("ACCEPTED")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].status",
                        hasItem("REJECTED")));
        assertShouldWhereHost(fx.containerX(), fx.hostB());
        assertShouldWhereHost(fx.containerY(), fx.hostA());

        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.status", not("CLOSED")))
                .andExpect(jsonPath("$.data.pendingCloseReminderVisible", is(true)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostB())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(fx.hostB())));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", fx.containerX())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.track", is("OBSERVED")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(fx.hostB())))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostB())));

        mockMvc.perform(get("/api/conflicts/{id}/events", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventType", hasItem("ITEM_REJECTED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("ITEM_ACCEPTED")))
                .andExpect(jsonPath("$.data[*].eventType", hasItem("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data[?(@.eventType=='ITEM_ACCEPTED')].detail.wroteCurated",
                        hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.eventType=='ITEM_ACCEPTED')].detail.hint",
                        hasItem("条目已接受（含写入）")))
                .andExpect(jsonPath("$.data[?(@.eventType=='ITEM_REJECTED')].detail.wroteCurated",
                        hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.eventType=='ITEM_REJECTED')].detail.hint",
                        hasItem("条目已拒绝")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER")));
        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
    }

    @Test
    void nonHandlerAndPendingHandlerCannotReviewDraftItems() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-nhi-a", "ccd-nhi-b", "ctr-ccd-nhi-x", "ctr-ccd-nhi-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk());
        JsonNode draft = readOpenDraft(fx.conflictId());
        String draftId = draft.path("id").asText();
        String itemX = itemIdForSubject(draft, fx.containerX());
        String itemY = itemIdForSubject(draft, fx.containerY());

        postItemDecision(draftId, itemY, "reject", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        postItemDecision(draftId, itemX, "accept", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        assertShouldWhereHost(fx.containerX(), fx.hostA());
        assertShouldWhereHost(fx.containerY(), fx.hostA());

        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + GENERAL_2_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_2_ID)));

        postItemDecision(draftId, itemX, "accept", GENERAL_2_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        postItemDecision(draftId, itemY, "reject", GENERAL_2_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        postItemDecision(draftId, itemX, "accept", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        assertShouldWhereHost(fx.containerX(), fx.hostA());
        assertShouldWhereHost(fx.containerY(), fx.hostA());
        mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_2_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].status",
                        hasItem("PENDING")));
    }

    @Test
    void rejectingMergeKeyAndAcceptingSiblingDoesNotPendingCloseMergeKeyConflict() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-anti-a", "ccd-anti-b", "ctr-ccd-anti-x", "ctr-ccd-anti-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk());
        JsonNode draft = readOpenDraft(fx.conflictId());
        String draftId = draft.path("id").asText();
        String itemX = itemIdForSubject(draft, fx.containerX());
        String itemY = itemIdForSubject(draft, fx.containerY());

        postItemDecision(draftId, itemX, "reject", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerX() + "')].status",
                        hasItem("REJECTED")));
        postItemDecision(draftId, itemY, "accept", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.containerY() + "')].status",
                        hasItem("ACCEPTED")));

        assertShouldWhereHost(fx.containerX(), fx.hostA());
        assertShouldWhereHost(fx.containerY(), fx.hostB());

        mockMvc.perform(get("/api/conflicts/{id}", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.status", not("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.pendingCloseReminderVisible", is(false)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.hostA())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(fx.hostB())));

        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", fx.containerX())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(fx.conflictId())))
                .andExpect(jsonPath("$.data.status", is("OPEN")));
    }

    @Test
    void terminalDraftItemsCannotBeReviewedAgain() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-term-a", "ccd-term-b", "ctr-ccd-term-x", "ctr-ccd-term-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk());
        JsonNode draft = readOpenDraft(fx.conflictId());
        String draftId = draft.path("id").asText();
        String itemX = itemIdForSubject(draft, fx.containerX());
        String itemY = itemIdForSubject(draft, fx.containerY());

        postItemDecision(draftId, itemY, "reject", GENERAL_ID)
                .andExpect(status().isOk());
        postItemDecision(draftId, itemY, "reject", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_ITEM_NOT_PENDING")));
        postItemDecision(draftId, itemY, "accept", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_ITEM_NOT_PENDING")));
        assertShouldWhereHost(fx.containerY(), fx.hostA());

        postItemDecision(draftId, itemX, "accept", GENERAL_ID)
                .andExpect(status().isOk());
        postItemDecision(draftId, itemX, "accept", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_ITEM_NOT_PENDING")));
        postItemDecision(draftId, itemX, "reject", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_ITEM_NOT_PENDING")));
        assertShouldWhereHost(fx.containerX(), fx.hostB());
    }

    @Test
    void nonHandlerAndPendingHandlerCannotSelectChangeCurated() throws Exception {
        Fixture claimed = openConflictWithSiblingAndClaim("ccd-nh-a", "ccd-nh-b", "ctr-ccd-nh-x", "ctr-ccd-nh-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, claimed.conflictId(), GENERAL_ID);
        postBranch(claimed.conflictId(), SENIOR_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

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
    void openDraftRejectsSecondChangeCuratedAndFixActual() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-od-a", "ccd-od-b", "ctr-ccd-od-x", "ctr-ccd-od-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));

        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_ALREADY_OPEN")));
        postBranch(fx.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("OPEN_DRAFT_BLOCKS_FIX_ACTUAL")));
    }

    @Test
    void fixActualStillSkipsDraftAndBlocksChangeCuratedWhilePlanActive() throws Exception {
        Fixture fx = openConflictWithSiblingAndClaim("ccd-pl-a", "ccd-pl-b", "ctr-ccd-pl-x", "ctr-ccd-pl-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        postBranch(fx.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.selectedForkId", is("FIX_ACTUAL_TO_CURATED")));

        mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));

        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", fx.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)));

        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_ALREADY_ACTIVE")));
    }

    private org.springframework.test.web.servlet.ResultActions postItemDecision(
            String draftId, String itemId, String decision, String userId
    ) throws Exception {
        return mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/{decision}",
                        draftId, itemId, decision)
                .header(TempAuthHeaders.USER_ID, userId)
                .accept(MediaType.APPLICATION_JSON));
    }

    private JsonNode readOpenDraft(String conflictId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String itemIdForSubject(JsonNode draft, String subjectId) {
        for (JsonNode item : draft.path("items")) {
            if (subjectId.equals(item.path("subjectId").asText())) {
                return item.path("id").asText();
            }
        }
        throw new AssertionError("No draft item for subject " + subjectId);
    }

    private void assertShouldWhereHost(String containerId, String hostId) throws Exception {
        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostId)));
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
