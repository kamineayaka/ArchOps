package com.archops.conflict;

import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.mapper.ObservedFactMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046 ticket 04: 确认关闭 needs no user identity and records no operator.
 */
@HttpAcceptanceTest
class AnonymousConfirmCloseHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObservedFactMapper observedFactMapper;

    @Test
    void confirmCloseWithoutIdentityWhenTracksEqual() throws Exception {
        String conflictId = pendingCloseWithoutHandler("acc-a", "acc-b", "ctr-acc-001", "ctr-acc-001-y");

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='CLOSED')].actorUserId", everyItem(nullValue())));
    }

    @Test
    void confirmCloseWithUserHeaderStillOmitsActor() throws Exception {
        String conflictId = pendingCloseWithoutHandler("acc-h-a", "acc-h-b", "ctr-acc-002", "ctr-acc-002-y");

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='CLOSED')].actorUserId", everyItem(nullValue())));
    }

    @Test
    void confirmCloseWhenTracksDriftStaysUnclosed() throws Exception {
        PendingClose world = pendingCloseWithoutHandlerWorld(
                "acc-d-a", "acc-d-b", "ctr-acc-003", "ctr-acc-003-y");
        observedFactMapper.update(null, new LambdaUpdateWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, world.containerX())
                .set(ObservedFact::getAvailability, ObservedAvailability.PRESENT)
                .set(ObservedFact::getTargetId, world.hostA()));

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", world.conflictId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_ALIGNED")));

        mockMvc.perform(get("/api/conflicts/{id}", world.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", not("CLOSED")));
    }

    @Test
    void confirmCloseWhenNotPendingCloseIsRejected() throws Exception {
        String conflictId = openConflictWithoutHandler("acc-o-a", "acc-o-b", "ctr-acc-004");

        mockMvc.perform(post("/api/conflicts/{id}/confirm-close", conflictId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONFLICT_NOT_PENDING_CLOSE")));

        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")));
    }

    private record PendingClose(String conflictId, String hostA, String containerX) {
    }

    private String pendingCloseWithoutHandler(
            String hostAName,
            String hostBName,
            String objectX,
            String objectY
    ) throws Exception {
        return pendingCloseWithoutHandlerWorld(hostAName, hostBName, objectX, objectY).conflictId();
    }

    private PendingClose pendingCloseWithoutHandlerWorld(
            String hostAName,
            String hostBName,
            String objectX,
            String objectY
    ) throws Exception {
        String hostA = createHost(hostAName);
        String hostB = createHost(hostBName);
        String containerX = createContainer("app-" + objectX, objectX);
        String containerY = createContainer("app-" + objectY, objectY);
        confirmRunsOn(containerX, hostA);
        confirmRunsOn(containerY, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectX, objectX);
        MvcResult conflict = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String conflictId = objectMapper.readTree(conflict.getResponse().getContentAsString())
                .path("data").path("id").asText();
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"CHANGE_CURATED_TO_OBSERVED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        MvcResult draft = mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String itemX = itemId(
                objectMapper.readTree(draft.getResponse().getContentAsString()).path("data").path("items"),
                containerX);
        mockMvc.perform(post(
                        "/api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/accept",
                        conflictId, itemX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conflicts/{id}", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")));
        return new PendingClose(conflictId, hostA, containerX);
    }

    private String openConflictWithoutHandler(String hostAName, String hostBName, String objectId) throws Exception {
        String hostA = createHost(hostAName);
        String hostB = createHost(hostBName);
        String containerId = createContainer("app-" + objectId, objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);
        MvcResult result = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private static String itemId(JsonNode items, String subjectId) {
        for (JsonNode item : items) {
            if (subjectId.equals(item.path("subjectId").asText())) {
                return item.path("id").asText();
            }
        }
        throw new AssertionError("No 草案 item for subject " + subjectId);
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }
}
