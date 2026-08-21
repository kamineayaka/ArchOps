package com.archops.curated;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.observed.domain.HostAgent;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Change-curated ticket 06 HTTP tracer suite: ordered happy path + Spec negatives.
 * {@code @Order} records Spec order only; each method builds its own fixture.
 */
@HttpAcceptanceTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
class ChangeCuratedDraftTracerHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String GENERAL_2_ID = "user-general-2-demo";
    private static final String SENIOR_ID = "user-senior-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HostAgentMapper hostAgentMapper;

    @Test
    @Order(1)
    void happyPath_hostsAB_curatedRunsOnA_snapshotXOnB_claim_changeCurated_rejectY_acceptX_pendingClose_confirmClose()
            throws Exception {
        World world = bootstrapHostsABCuratedXYOnA("ccd06-hp");

        // 1. 建底：主机 A/B；容器 X、Y；策展 X/Y 皆 运行于 A
        getShouldWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));
        getShouldWhere(world.containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));

        // 2. Agent 快照：仅 X 在 B。冲突 OPEN；诊断可仍 PENDING
        heartbeatXOnHost(world, world.hostB(), world.agentIdOnB());
        MvcResult warn = getByMergeKey(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(world.hostB())))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.mergeKey.relationLabel", is("运行于")))
                .andReturn();
        String conflictId = readDataId(warn);

        // 3. 一般角色认领 → 已接受处理人
        claimAsAcceptedHandler(conflictId);

        // 4. 诊断 READY：分叉同时含 FIX_ACTUAL 与 CHANGE_CURATED
        waitUntilDiagnosisReady(conflictId);
        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.forks[*].id",
                        hasItems("FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='FIX_ACTUAL_TO_CURATED')].kind",
                        hasItem("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')].kind",
                        hasItem("CHANGE_CURATED")));

        // 5. 处理人选改理想 → 开放草案 ≥2 条；策展 X 仍为 A；无活跃操作计划
        postBranch(conflictId, GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isOk());
        MvcResult open = getOpenDraft(conflictId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerX() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerY() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerX() + "')].kind",
                        hasItem("RUNS_ON_TARGET_CHANGE")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerY() + "')].kind",
                        hasItem("RUNS_ON_TARGET_CHANGE")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerX() + "')].fromHostId",
                        hasItem(world.hostA())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerX() + "')].toHostId",
                        hasItem(world.hostB())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerY() + "')].fromHostId",
                        hasItem(world.hostA())))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + world.containerY() + "')].toHostId",
                        hasItem(world.hostB())))
                .andReturn();
        JsonNode items = objectMapper.readTree(open.getResponse().getContentAsString())
                .path("data").path("items");
        String itemX = itemId(items, world.containerX());
        String itemY = itemId(items, world.containerY());
        getShouldWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));
        getActivePlan(conflictId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));

        // 6. 非处理人接受合并键 X → 拒绝；策展仍为 A
        postItemAction(conflictId, itemX, "accept", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
        getShouldWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));

        // 7. 处理人拒绝 Y → REJECTED；Y「应该在哪」仍为 A
        postItemAction(conflictId, itemY, "reject", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + itemY + "')].status",
                        hasItem("REJECTED")));
        getShouldWhere(world.containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));

        // 8. 处理人接受 X → ACCEPTED；X「应该在哪」为 B；Y 仍为 A
        postItemAction(conflictId, itemX, "accept", GENERAL_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + itemX + "')].status",
                        hasItem("ACCEPTED")));
        getShouldWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostB())));
        getShouldWhere(world.containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));

        // 9. 不发新快照。冲突 PENDING_CLOSE（不是 CLOSED）；策展 B = 观测 B
        getConflict(conflictId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.status", not("CLOSED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostB())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(world.hostB())));

        // 10. 处理人确认关闭 → CLOSED；再 GET 仍 CLOSED
        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
        getConflict(conflictId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
    }

    @Test
    @Order(2)
    void selectChangeCuratedDoesNotWriteCuratedBeforeAnyItemAccept() throws Exception {
        ClaimedConflict fx = claimedReadyConflict("ccd06-n1");
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isOk());

        getShouldWhere(fx.world().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.world().hostA())));
        getShouldWhere(fx.world().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(fx.world().hostA())));

        getOpenDraft(fx.conflictId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.world().containerX() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.subjectId=='" + fx.world().containerY() + "')].status",
                        hasItem("PENDING")));
    }

    @Test
    @Order(3)
    void nonHandlerAndPendingAcceptCannotSelectChangeCurated() throws Exception {
        ClaimedConflict claimed = claimedReadyConflict("ccd06-n2s-nh");
        postBranch(claimed.conflictId(), SENIOR_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));

        String pendingId = openUnclaimedConflict("ccd06-n2s-pe");
        acknowledgeAndAssignPending(pendingId, GENERAL_ID);
        waitUntilDiagnosisReady(pendingId);
        postBranch(pendingId, GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")));
    }

    @Test
    @Order(4)
    void nonHandlerAndPendingAcceptCannotReviewDraftItems() throws Exception {
        OpenDraft nh = openChangeCuratedDraft("ccd06-n2r-nh");
        postItemAction(nh.conflictId(), nh.itemXId(), "accept", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")))
                .andExpect(jsonPath("$.data", nullValue()));
        postItemAction(nh.conflictId(), nh.itemYId(), "reject", SENIOR_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")))
                .andExpect(jsonPath("$.data", nullValue()));
        getShouldWhere(nh.world().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(nh.world().hostA())));
        getShouldWhere(nh.world().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(nh.world().hostA())));

        OpenDraft pending = openChangeCuratedDraft("ccd06-n2r-pe");
        transferHandlerPending(pending.conflictId(), GENERAL_ID, GENERAL_2_ID);
        postItemAction(pending.conflictId(), pending.itemXId(), "accept", GENERAL_2_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")))
                .andExpect(jsonPath("$.data", nullValue()));
        postItemAction(pending.conflictId(), pending.itemYId(), "reject", GENERAL_2_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_REQUIRES_ACCEPTED_HANDLER")))
                .andExpect(jsonPath("$.data", nullValue()));
        getShouldWhere(pending.world().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(pending.world().hostA())));
        getShouldWhere(pending.world().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(pending.world().hostA())));
    }

    @Test
    @Order(5)
    void fixActualStillSkipsDraftAndCreatesOperationPlan() throws Exception {
        ClaimedConflict fx = claimedReadyConflict("ccd06-n3");
        MvcResult created = postBranch(fx.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andReturn();
        String planId = readDataId(created);

        getOpenDraft(fx.conflictId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));
        getActivePlan(fx.conflictId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(planId)))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")));
    }

    @Test
    @Order(6)
    void openDraftBlocksFixActualSelection() throws Exception {
        OpenDraft draft = openChangeCuratedDraft("ccd06-n4a");
        postBranch(draft.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("OPEN_DRAFT_BLOCKS_FIX_ACTUAL")));
        getOpenDraft(draft.conflictId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));
        getActivePlan(draft.conflictId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));
    }

    @Test
    @Order(7)
    void activePlanBlocksChangeCuratedSelection() throws Exception {
        ClaimedConflict fx = claimedReadyConflict("ccd06-n4b");
        postBranch(fx.conflictId(), GENERAL_ID, "FIX_ACTUAL_TO_CURATED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")));
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("PLAN_ALREADY_ACTIVE")));
        getOpenDraft(fx.conflictId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));
    }

    @Test
    @Order(8)
    void bootstrapPostRejectsOverwriteOfExistingRunsOn() throws Exception {
        String hostA = createHost("ccd06-n5-a");
        String hostB = createHost("ccd06-n5-b");
        String containerZ = createContainer("app-ccd06-n5-z", "ccd06-n5-z");
        confirmRunsOn(containerZ, hostA);

        postRunsOn(containerZ, hostB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_RUNS_ON_EXISTS")))
                .andExpect(jsonPath("$.data", nullValue()));
        postRunsOn(containerZ, hostA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_RUNS_ON_EXISTS")))
                .andExpect(jsonPath("$.data", nullValue()));

        getShouldWhere(containerZ)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));
    }

    @Test
    @Order(9)
    void snapshotBtoCWhileDraftPendingUpgradesSameConflictAndVoidsDraft() throws Exception {
        OpenDraft draft = openChangeCuratedDraft("ccd06-n6");
        String hostC = snapshotXOnHostC(draft, "ccd06-n6-c");

        getConflict(draft.conflictId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(draft.conflictId())))
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.world().hostA())))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostC)))
                .andExpect(jsonPath("$.data.observedLineage", hasSize(2)))
                .andExpect(jsonPath("$.data.observedLineage[0].hostId", is(draft.world().hostB())))
                .andExpect(jsonPath("$.data.observedLineage[1].hostId", is(hostC)));

        assertEquals(1, countActiveForSubject(draft.world().containerX()));

        getShouldWhere(draft.world().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.world().hostA())));
        getShouldWhere(draft.world().containerY())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.world().hostA())));

        getOpenDraft(draft.conflictId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")))
                .andExpect(jsonPath("$.data", nullValue()));
        getDraftById(draft.conflictId(), draft.draftId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemXId() + "')].status",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + draft.itemYId() + "')].status",
                        hasItem("PENDING")));
        postItemAction(draft.conflictId(), draft.itemXId(), "accept", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_VOIDED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    @Order(10)
    void heartbeatTimeoutWhileDraftOpenSuspendsConflictAndVoidsDraft() throws Exception {
        OpenDraft draft = openChangeCuratedDraft("ccd06-n7");
        rewindHeartbeat(draft.world().agentIdOnB());

        mockMvc.perform(post("/api/observed/scan-heartbeat-timeouts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        getConflict(draft.conflictId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUSPENDED")))
                .andExpect(jsonPath("$.data.status", not("CLOSED")))
                .andExpect(jsonPath("$.data.status", not("OPEN")))
                .andExpect(jsonPath("$.data.observationHollow", is(true)));

        getActualWhere(draft.world().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.observedValue.availability", is("HOLLOW")))
                .andExpect(jsonPath("$.data.observedValue.hostId", nullValue()));
        getShouldWhere(draft.world().containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(draft.world().hostA())));

        getOpenDraft(draft.conflictId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_NOT_FOUND")));
        getDraftById(draft.conflictId(), draft.draftId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOIDED")));
        postItemAction(draft.conflictId(), draft.itemXId(), "accept", GENERAL_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DRAFT_VOIDED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    private World bootstrapHostsABCuratedXYOnA(String prefix) throws Exception {
        String objectX = prefix + "-x";
        String objectY = prefix + "-y";
        String hostA = createHost(prefix + "-a");
        String hostB = createHost(prefix + "-b");
        String containerX = createContainer("app-" + objectX, objectX);
        String containerY = createContainer("app-" + objectY, objectY);
        confirmRunsOn(containerX, hostA);
        confirmRunsOn(containerY, hostA);
        return new World(hostA, hostB, containerX, containerY, objectX, objectY);
    }

    private void heartbeatXOnHost(World world, String hostId, String agentId) throws Exception {
        heartbeatWithContainer(hostId, agentId, world.objectX());
    }

    private void claimAsAcceptedHandler(String conflictId) throws Exception {
        mockMvc.perform(post("/api/conflicts/{id}/claim", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("ACCEPTED")))
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(GENERAL_ID)));
    }

    private void waitUntilDiagnosisReady(String conflictId) throws Exception {
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
    }

    private ClaimedConflict claimedReadyConflict(String prefix) throws Exception {
        World world = bootstrapHostsABCuratedXYOnA(prefix);
        heartbeatXOnHost(world, world.hostB(), world.agentIdOnB());
        String conflictId = readDataId(getByMergeKey(world.containerX())
                .andExpect(status().isOk())
                .andReturn());
        claimAsAcceptedHandler(conflictId);
        waitUntilDiagnosisReady(conflictId);
        return new ClaimedConflict(world, conflictId);
    }

    private String openUnclaimedConflict(String prefix) throws Exception {
        World world = bootstrapHostsABCuratedXYOnA(prefix);
        heartbeatXOnHost(world, world.hostB(), world.agentIdOnB());
        return readDataId(getByMergeKey(world.containerX())
                .andExpect(status().isOk())
                .andReturn());
    }

    private void acknowledgeAndAssignPending(String conflictId, String assigneeUserId) throws Exception {
        mockMvc.perform(post("/api/conflicts/{id}/acknowledge", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("NONE")));
        mockMvc.perform(post("/api/conflicts/{id}/assign-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":\"" + assigneeUserId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")));
    }

    private OpenDraft openChangeCuratedDraft(String prefix) throws Exception {
        ClaimedConflict fx = claimedReadyConflict(prefix);
        postBranch(fx.conflictId(), GENERAL_ID, "CHANGE_CURATED_TO_OBSERVED")
                .andExpect(status().isOk());
        MvcResult open = getOpenDraft(fx.conflictId())
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(open.getResponse().getContentAsString()).path("data");
        JsonNode items = data.path("items");
        return new OpenDraft(
                fx.world(),
                fx.conflictId(),
                data.path("id").asText(),
                itemId(items, fx.world().containerX()),
                itemId(items, fx.world().containerY()));
    }

    private void transferHandlerPending(String conflictId, String fromUserId, String toUserId) throws Exception {
        mockMvc.perform(post("/api/conflicts/{id}/transfer-handler", conflictId)
                        .header(TempAuthHeaders.USER_ID, fromUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + toUserId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collaboration.handlerUserId", is(toUserId)))
                .andExpect(jsonPath("$.data.collaboration.handlerAcceptance", is("PENDING_ACCEPT")));
    }

    private String snapshotXOnHostC(OpenDraft draft, String hostCName) throws Exception {
        String hostC = createHost(hostCName);
        heartbeatXOnHost(draft.world(), hostC, draft.world().agentIdOnC());
        return hostC;
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

    private ResultActions postBranch(String conflictId, String userId, String forkId) throws Exception {
        return mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                .header(TempAuthHeaders.USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forkId\":\"" + forkId + "\"}")
                .accept(MediaType.APPLICATION_JSON));
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

    private ResultActions getOpenDraft(String conflictId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getDraftById(String conflictId, String draftId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{conflictId}/curated-drafts/{draftId}",
                conflictId, draftId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getActivePlan(String conflictId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", conflictId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getConflict(String conflictId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getByMergeKey(String subjectId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/by-merge-key")
                .param("subjectId", subjectId)
                .param("relationType", "RUNS_ON")
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getShouldWhere(String containerId) throws Exception {
        return mockMvc.perform(get("/api/curated/asks/should-where")
                .param("containerId", containerId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions getActualWhere(String containerId) throws Exception {
        return mockMvc.perform(get("/api/observed/asks/actual-where")
                .param("containerId", containerId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private void rewindHeartbeat(String agentId) {
        hostAgentMapper.update(null, new LambdaUpdateWrapper<HostAgent>()
                .eq(HostAgent::getAgentId, agentId)
                .set(HostAgent::getLastHeartbeatAt, Instant.now().minus(2, ChronoUnit.MINUTES)));
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
        postRunsOn(containerId, hostId).andExpect(status().isOk());
    }

    private ResultActions postRunsOn(String containerId, String hostId) throws Exception {
        return mockMvc.perform(post("/api/curated/facts/runs-on")
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"containerId\":\"" + containerId + "\",\"hostId\":\"" + hostId + "\"}")
                .accept(MediaType.APPLICATION_JSON));
    }

    private String readDataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private static String itemId(JsonNode items, String subjectId) {
        for (JsonNode item : items) {
            if (subjectId.equals(item.path("subjectId").asText())) {
                return item.path("id").asText();
            }
        }
        throw new AssertionError("No 草案 item for subject " + subjectId);
    }

    private record OpenDraft(
            World world,
            String conflictId,
            String draftId,
            String itemXId,
            String itemYId
    ) {
    }

    private record ClaimedConflict(World world, String conflictId) {
    }

    private record World(
            String hostA,
            String hostB,
            String containerX,
            String containerY,
            String objectX,
            String objectY
    ) {
        String agentIdOnB() {
            return "agent-" + objectX;
        }

        String agentIdOnC() {
            return "agent-" + objectX + "-c";
        }
    }
}
