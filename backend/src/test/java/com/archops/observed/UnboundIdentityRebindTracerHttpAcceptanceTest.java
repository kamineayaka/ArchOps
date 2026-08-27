package com.archops.observed;

import com.archops.conflict.ConflictDiagnosisWait;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unbound ticket 06 HTTP tracer suite: ordered happy path + Spec negatives.
 * {@code @Order} records Spec order only; each method builds its own fixture.
 */
@HttpAcceptanceTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
class UnboundIdentityRebindTracerHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void happyPath_curatedThenMissingLabel_unboundIdentityLost_draftAndBind_labelMatchRestoresUpgradeChain()
            throws Exception {
        World world = bootstrapHostsACCuratedXOnA("u06hp");

        // 1. 建底：主机 A/C（及可选 B）；策展容器 X 运行于 A；现场未打标
        getShouldWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));

        // 2. Agent 在 A 上报缺标 runtimeId=r1 → 待并入 MISSING_LABEL；X 身份失联；
        // 「实际在哪」不得把 A 报成可用实际；by-merge-key 不承诺升级链
        heartbeatUnlabeled(world.hostA(), world.agentA(), world.runtimeR1(), world.nameSimilarToX());
        JsonNode unlabeled = unboundByRuntimeId(listUnbound(), world.runtimeR1());
        assertThat(unlabeled, notNullValue());
        assertThat(unlabeled.path("reason").asText(), is("MISSING_LABEL"));
        assertThat(unlabeled.path("upgradeChainPromised").asBoolean(), is(false));
        getIdentityLost(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")))
                .andExpect(jsonPath("$.data.sourceHostId", is(world.hostA())))
                .andExpect(jsonPath("$.data.upgradeChainPromised", is(false)));
        getActualWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")))
                .andExpect(jsonPath("$.data.observedValue.availability", not("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));
        getByMergeKey(world.containerX())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        // 3. Agent 在 C 的快照不含 X：不得单独给 X 打失联（失联标仍来自 A）
        heartbeatUnlabeled(world.hostC(), world.agentC(), world.runtimeOnC(), "u06hp-c-other");
        getIdentityLost(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceHostId", is(world.hostA())))
                .andExpect(jsonPath("$.data.sourceHostId", not(world.hostC())));
        JsonNode onC = unboundByRuntimeId(listUnbound(), world.runtimeOnC());
        assertThat(onC, notNullValue());
        assertThat(onC.path("sourceHostId").asText(), is(world.hostC()));

        // 4. A 上第二个 runtime、标签 never-curated → UNKNOWN_OBJECT_ID；
        // POST 未绑定草案 ≥2 条（新建 + 运行于 A）；接受前无该策展对象
        heartbeatUnlabeledAndLabeled(
                world.hostA(), world.agentA(),
                world.runtimeR1(), world.nameSimilarToX(),
                world.runtimeUnknown(), world.nameUnknown(), world.neverObjectId());
        JsonNode unknown = unboundByRuntimeId(listUnbound(), world.runtimeUnknown());
        assertThat(unknown, notNullValue());
        assertThat(unknown.path("reason").asText(), is("UNKNOWN_OBJECT_ID"));
        OpenUnboundDraft unknownDraft = openDraftFromRuntime(world.runtimeUnknown());
        assertThat(unknownDraft.items().size() >= 2, is(true));
        assertThat(unknownDraft.items().stream().map(n -> n.path("kind").asText()).toList(),
                hasItems("CREATE_CONTAINER_FROM_UNBOUND", "CURATED_RUNS_ON_INSERT"));
        assertThat(unknownDraft.conflictId(), nullValue());
        getDraft(unknownDraft.draftId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.origin", is("UNBOUND_CANDIDATE")))
                .andExpect(jsonPath("$.data.conflictId").value(nullValue()));
        JsonNode createItem = itemByKind(unknownDraft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        JsonNode runsOnItem = itemByKind(unknownDraft.items(), "CURATED_RUNS_ON_INSERT");
        assertThat(createItem.path("subjectId").isNull() || createItem.path("subjectId").isMissingNode()
                || createItem.path("subjectId").asText().isBlank(), is(true));
        assertThat(createItem.path("payload").path("immutableObjectId").asText(), is(world.neverObjectId()));
        assertThat(runsOnItem.path("toHostId").asText(), is(world.hostA()));

        // 5. 拒 运行于、接受新建 → 策展对象存在且带该标签、无策展 运行于。
        // 再心跳标签命中 → 观测 运行于 A；不得仅因两侧同意就新开冲突或进入待确认关闭
        postUnboundItem(unknownDraft.draftId(), runsOnItem.path("id").asText(), "reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='CURATED_RUNS_ON_INSERT')].status",
                        hasItem("REJECTED")));
        MvcResult acceptedCreate = postUnboundItem(
                unknownDraft.draftId(), createItem.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='CREATE_CONTAINER_FROM_UNBOUND')].status",
                        hasItem("ACCEPTED")))
                .andReturn();
        String createdId = itemByKind(
                sortedItems(objectMapper.readTree(acceptedCreate.getResponse().getContentAsString())
                        .path("data").path("items")),
                "CREATE_CONTAINER_FROM_UNBOUND").path("subjectId").asText();
        assertThat(createdId, notNullValue());
        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"u06hp-probe\",\"objectId\":\"" + world.neverObjectId() + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CURATED_OBJECT_ID_EXISTS")));
        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", createdId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CURATED_RUNS_ON_NOT_FOUND")));
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unlabeledAndLabeledSnapshot(
                                world.hostA(), world.agentA(),
                                world.runtimeR1(), world.nameSimilarToX(),
                                world.runtimeUnknown(), world.nameUnknown(), world.neverObjectId()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched[0].curatedContainerId", is(createdId)))
                .andExpect(jsonPath("$.data.matched[0].observedHostId", is(world.hostA())));
        getByMergeKey(createdId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));
        getByMergeKey(world.containerX())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        // 6. 从 r1 发草案：绑定 X vs 新建。双接受 → 第二次失败。只接受绑定 →
        // 不写可靠观测 运行于；r1 离开待并入；X 仍身份失联
        OpenUnboundDraft lostDraft = openDraftFromRuntime(world.runtimeR1());
        assertThat(lostDraft.items().stream().map(n -> n.path("kind").asText()).toList(),
                containsInAnyOrder("BIND_UNBOUND_TO_EXISTING", "CREATE_CONTAINER_FROM_UNBOUND"));
        getDraft(lostDraft.draftId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.origin", is("UNBOUND_CANDIDATE")))
                .andExpect(jsonPath("$.data.conflictId").value(nullValue()));
        JsonNode bindItem = itemByKind(lostDraft.items(), "BIND_UNBOUND_TO_EXISTING");
        JsonNode lostCreate = itemByKind(lostDraft.items(), "CREATE_CONTAINER_FROM_UNBOUND");
        assertThat(bindItem.path("subjectId").asText(), is(world.containerX()));
        postUnboundItem(lostDraft.draftId(), bindItem.path("id").asText(), "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind=='BIND_UNBOUND_TO_EXISTING')].status",
                        hasItem("ACCEPTED")));
        postUnboundItem(lostDraft.draftId(), lostCreate.path("id").asText(), "accept")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("UNBOUND_CANDIDATE_CONSUMED")))
                .andExpect(jsonPath("$.data").value(nullValue()));
        getActualWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")))
                .andExpect(jsonPath("$.data.observedValue.hostId").value(nullValue()));
        getIdentityLost(world.containerX())
                .andExpect(status().isOk());
        assertThat(unboundByRuntimeId(listUnbound(), world.runtimeR1()), nullValue());
        getByMergeKey(world.containerX())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        // 7. 再缺标心跳 r1 → 仍失联、不复活为可新建候选、仍无升级链
        heartbeatUnlabeledAndLabeled(
                world.hostA(), world.agentA(),
                world.runtimeR1(), world.nameSimilarToX(),
                world.runtimeUnknown(), world.nameUnknown(), world.neverObjectId());
        getIdentityLost(world.containerX())
                .andExpect(status().isOk());
        getActualWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")));
        assertThat(unboundByRuntimeId(listUnbound(), world.runtimeR1()), nullValue());
        getByMergeKey(world.containerX())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        // 8. A 上正确标签 archops.object_id=ctr-x 命中 → 清失联、消费绑定记忆、写观测 运行于；
        // 落在策展 A 则既有比对（无冲突）；升级链可恢复
        heartbeatLabeled(world.hostA(), world.agentA(), world.runtimeR1(), world.nameX(), world.objectX());
        getIdentityLost(world.containerX())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("IDENTITY_LOST_NOT_FOUND")));
        getActualWhere(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityLost", is(false)))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(world.hostA())));
        getByMergeKey(world.containerX())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")));

        // 9. 命中后放到另一策展宿主 B → OPEN 冲突；诊断可再出修实际/改理想
        heartbeatLabeled(world.hostB(), world.agentB(), world.runtimeOnB(), world.nameX(), world.objectX());
        MvcResult open = getByMergeKey(world.containerX())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.status", not("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.identityLost", is(false)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(world.hostB())))
                .andReturn();
        String conflictId = objectMapper.readTree(open.getResponse().getContentAsString())
                .path("data").path("id").asText();
        waitUntilDiagnosisReady(conflictId);
        mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.forks[*].id",
                        hasItems("FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")));
    }

    @Test
    @Order(2)
    void otherHostSnapshotDoesNotMarkIdentityLostOnX() throws Exception {
        String hostA = createHost("u06n1-ha");
        String hostC = createHost("u06n1-hc");
        String containerX = createContainer("u06n1-x", "u06n1-oid");
        confirmRunsOn(containerX, hostA);

        heartbeatUnlabeled(hostC, "u06n1-ag-c", "u06n1-rt-c", "u06n1-miss");

        getIdentityLost(containerX)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("IDENTITY_LOST_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));
        JsonNode candidate = unboundByRuntimeId(listUnbound(), "u06n1-rt-c");
        assertThat(candidate, notNullValue());
        assertThat(candidate.path("reason").asText(), is("MISSING_LABEL"));
        assertThat(candidate.path("sourceHostId").asText(), is(hostC));
        assertThat(candidate.path("upgradeChainPromised").asBoolean(), is(false));
    }

    @Test
    @Order(3)
    void unlabeledSameNameDoesNotPromiseUpgradeChain() throws Exception {
        String hostA = createHost("u06n2-h");
        String containerX = createContainer("u06n2-x", "u06n2-oid");
        confirmRunsOn(containerX, hostA);

        heartbeatUnlabeled(hostA, "u06n2-ag", "u06n2-rt", "u06n2-x");

        JsonNode candidate = unboundByRuntimeId(listUnbound(), "u06n2-rt");
        assertThat(candidate, notNullValue());
        assertThat(candidate.path("upgradeChainPromised").asBoolean(), is(false));
        getByMergeKey(containerX)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private World bootstrapHostsACCuratedXOnA(String prefix) throws Exception {
        String hostA = createHost(prefix + "-ha");
        String hostB = createHost(prefix + "-hb");
        String hostC = createHost(prefix + "-hc");
        String objectX = prefix + "-ctr-x";
        String containerX = createContainer(prefix + "-x", objectX);
        confirmRunsOn(containerX, hostA);
        return new World(prefix, hostA, hostB, hostC, containerX, objectX);
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

    private ResultActions getIdentityLost(String containerId) throws Exception {
        return mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
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

    private ResultActions getDraft(String draftId) throws Exception {
        return mockMvc.perform(get("/api/curated-drafts/{draftId}", draftId)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .accept(MediaType.APPLICATION_JSON));
    }

    private void waitUntilDiagnosisReady(String conflictId) throws Exception {
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
    }

    private void heartbeatUnlabeled(String hostId, String agentId, String runtimeId, String name)
            throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"%s",
                                      "name":"%s",
                                      "labels":{}
                                    }],
                                    "absentObjectIds":[]
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void heartbeatLabeled(
            String hostId, String agentId, String runtimeId, String name, String objectId)
            throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"%s",
                                      "name":"%s",
                                      "labels":{"archops.object_id":"%s"}
                                    }]
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name, objectId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void heartbeatUnlabeledAndLabeled(
            String hostId,
            String agentId,
            String unlabeledRuntimeId,
            String unlabeledName,
            String labeledRuntimeId,
            String labeledName,
            String objectId
    ) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unlabeledAndLabeledSnapshot(
                                hostId, agentId,
                                unlabeledRuntimeId, unlabeledName,
                                labeledRuntimeId, labeledName, objectId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private static String unlabeledAndLabeledSnapshot(
            String hostId,
            String agentId,
            String unlabeledRuntimeId,
            String unlabeledName,
            String labeledRuntimeId,
            String labeledName,
            String objectId
    ) {
        return """
                {
                  "agentId":"%s",
                  "hostId":"%s",
                  "snapshot":{
                    "containers":[
                      {
                        "runtimeId":"%s",
                        "name":"%s",
                        "labels":{}
                      },
                      {
                        "runtimeId":"%s",
                        "name":"%s",
                        "labels":{"archops.object_id":"%s"}
                      }
                    ],
                    "absentObjectIds":[]
                  }
                }
                """.formatted(
                agentId, hostId,
                unlabeledRuntimeId, unlabeledName,
                labeledRuntimeId, labeledName, objectId);
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
                .andExpect(jsonPath("$.data.origin", is("UNBOUND_CANDIDATE")))
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        String conflictId = data.path("conflictId").isNull() || data.path("conflictId").isMissingNode()
                ? null
                : data.path("conflictId").asText();
        return new OpenUnboundDraft(data.path("id").asText(), conflictId, sortedItems(data.path("items")));
    }

    private ResultActions postUnboundItem(String draftId, String itemId, String action) throws Exception {
        return mockMvc.perform(post("/api/curated-drafts/{draftId}/items/{itemId}/{action}",
                        draftId, itemId, action)
                .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON));
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

    private record OpenUnboundDraft(String draftId, String conflictId, List<JsonNode> items) {
    }

    private record World(
            String prefix,
            String hostA,
            String hostB,
            String hostC,
            String containerX,
            String objectX
    ) {
        String agentA() {
            return prefix + "-ag-a";
        }

        String agentB() {
            return prefix + "-ag-b";
        }

        String agentC() {
            return prefix + "-ag-c";
        }

        String runtimeR1() {
            return prefix + "-r1";
        }

        String runtimeUnknown() {
            return prefix + "-r-unknown";
        }

        String runtimeOnC() {
            return prefix + "-r-c";
        }

        String runtimeOnB() {
            return prefix + "-r-b";
        }

        String nameX() {
            return prefix + "-x";
        }

        String nameSimilarToX() {
            return prefix + "-similar";
        }

        String nameUnknown() {
            return prefix + "-unknown";
        }

        String neverObjectId() {
            return prefix + "-never";
        }
    }
}
