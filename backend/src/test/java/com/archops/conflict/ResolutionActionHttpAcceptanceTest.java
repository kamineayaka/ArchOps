package com.archops.conflict;

import com.archops.observed.domain.HostAgent;
import com.archops.observed.mapper.HostAgentMapper;
import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0047 ticket 01: a case lists resolution actions and does not carry a cause kind.
 */
@HttpAcceptanceTest
@TestPropertySource(properties = {
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
class ResolutionActionHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HostAgentMapper hostAgentMapper;

    @Test
    void readableUnequalListsRepairActualAndChangeIdealWithoutCauseKind() throws Exception {
        String hostA = createHost("a47a-ha");
        String hostB = createHost("a47a-hb");
        String containerId = createContainer("a47a-app", "a47a-oid");
        confirmRunsOn(containerId, hostA);
        heartbeatLabeled(hostB, "a47a-agent", "a47a-oid");

        MvcResult open = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        String conflictId = objectMapper.readTree(open.getResponse().getContentAsString())
                .path("data").path("id").asText();
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        MvcResult diagnosis = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andReturn();
        String body = diagnosis.getResponse().getContentAsString();
        JsonNode forks = objectMapper.readTree(body).path("data").path("forks");
        assertEquals(2, forks.size());
        Set<String> ids = new HashSet<>();
        for (JsonNode fork : forks) {
            assertFalse(fork.has("kind"), fork.toString());
            ids.add(fork.path("id").asText());
        }
        assertEquals(Set.of("FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED"), ids);
        assertEquals("修实际回策展宿主", fork(forks, "FIX_ACTUAL_TO_CURATED").path("label").asText());
        assertEquals("改理想", fork(forks, "CHANGE_CURATED_TO_OBSERVED").path("label").asText());
        assertFalse(body.contains("无法归类"));
        assertFalse(body.contains("UNCLASSIFIED"));
        assertFalse(body.contains("EXPLAIN_IDENTITY_LOST"));
    }

    @Test
    void hollowOffersOnlyRestoreObservationChannel() throws Exception {
        String objectId = "a47b-oid";
        String hostA = createHost("a47b-ha");
        String hostB = createHost("a47b-hb");
        String containerId = createContainer("a47b-app", objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatLabeled(hostB, "agent-" + objectId, objectId);

        MvcResult open = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        String conflictId = objectMapper.readTree(open.getResponse().getContentAsString())
                .path("data").path("id").asText();
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        hostAgentMapper.update(null, new LambdaUpdateWrapper<HostAgent>()
                .eq(HostAgent::getAgentId, "agent-" + objectId)
                .set(HostAgent::getLastHeartbeatAt, Instant.now().minus(2, ChronoUnit.MINUTES)));
        mockMvc.perform(post("/api/observed/scan-heartbeat-timeouts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspendedConflictIds", hasItem(conflictId)));
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        MvcResult diagnosis = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andReturn();
        String body = diagnosis.getResponse().getContentAsString();
        JsonNode forks = objectMapper.readTree(body).path("data").path("forks");
        assertEquals(1, forks.size());
        JsonNode only = forks.get(0);
        assertFalse(only.has("kind"), only.toString());
        assertEquals("RESTORE_HEARTBEAT_CHANNEL", only.path("id").asText());
        assertEquals("恢复观测通道", only.path("label").asText());
        assertFalse(body.contains("FIX_ACTUAL_TO_CURATED"));
        assertFalse(body.contains("CHANGE_CURATED_TO_OBSERVED"));
        assertFalse(body.contains("无法归类"));
    }

    private static JsonNode fork(JsonNode forks, String id) {
        for (JsonNode fork : forks) {
            if (id.equals(fork.path("id").asText())) {
                return fork;
            }
        }
        throw new AssertionError("Fork not found: " + id);
    }

    private void heartbeatLabeled(String hostId, String agentId, String objectId) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[{
                                      "runtimeId":"a47a-rt",
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
