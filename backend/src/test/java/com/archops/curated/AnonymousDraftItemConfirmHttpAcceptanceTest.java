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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046 ticket 03: 改策展草案逐条确认 needs no user identity and records no operator.
 */
@HttpAcceptanceTest
class AnonymousDraftItemConfirmHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void acceptRunsOnWithoutIdentityWritesCuratedAndRecordsNoActor() throws Exception {
        OpenChangeCurated world = openChangeCuratedDraft(
                "adi-a", "adi-b", "ctr-adi-001", "ctr-adi-001-y");

        mockMvc.perform(post(
                        "/api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/accept",
                        world.conflictId(), world.itemXId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + world.itemXId() + "')].status",
                        everyItem(is("ACCEPTED"))));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", world.containerX())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostB())));

        mockMvc.perform(get("/api/conflicts/{id}/events", world.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_ACCEPTED')].actorUserId",
                        everyItem(nullValue())));

        mockMvc.perform(get("/api/conflicts/{id}", world.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CLOSE")))
                .andExpect(jsonPath("$.data.status", not("CLOSED")));
    }

    @Test
    void rejectRunsOnWithoutIdentityLeavesCuratedAndRecordsNoActor() throws Exception {
        OpenChangeCurated world = openChangeCuratedDraft(
                "adi-rj-a", "adi-rj-b", "ctr-adi-002", "ctr-adi-002-y");

        mockMvc.perform(post(
                        "/api/conflicts/{conflictId}/curated-drafts/open/items/{itemId}/reject",
                        world.conflictId(), world.itemYId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + world.itemYId() + "')].status",
                        everyItem(is("REJECTED"))));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", world.containerY())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(world.hostA())));

        mockMvc.perform(get("/api/conflicts/{id}/events", world.conflictId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_ITEM_REJECTED')].actorUserId",
                        everyItem(nullValue())));
    }

    private OpenChangeCurated openChangeCuratedDraft(
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.origin", is("CHANGE_CURATED")));
        MvcResult draft = mockMvc.perform(get("/api/conflicts/{id}/curated-drafts/open", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(draft.getResponse().getContentAsString())
                .path("data").path("items");
        return new OpenChangeCurated(
                conflictId, hostA, hostB, containerX, containerY,
                itemId(items, containerX), itemId(items, containerY));
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
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }

    private record OpenChangeCurated(
            String conflictId,
            String hostA,
            String hostB,
            String containerX,
            String containerY,
            String itemXId,
            String itemYId
    ) {
    }
}
