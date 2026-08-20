package com.archops.conflict;

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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 06 HTTP acceptance: async diagnosis (rules Must), sensitive-read deny.
 * Change-curated ticket 02: mismatch diagnosis also emits a read-only CHANGE_CURATED fork.
 */
@HttpAcceptanceTest
class ConflictDiagnosisHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void warningExistsBeforeDiagnosisReadyAndRulesProduceFixActualFork() throws Exception {
        MismatchFixture fx = seedAvailableRunsOnMismatch("diag-a", "diag-b", "app-diag", "ctr-diag-001");
        mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", fx.containerId())
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.diagnosisStatus", anyOf(is("PENDING"), is("READY"))));
        assertTrue(fx.conflictId().startsWith("cnf-"));

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        getDiagnosis(fx.conflictId(), GENERAL_ID)
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.source", is("RULES")))
                .andExpect(jsonPath("$.data.summary", notNullValue()))
                .andExpect(jsonPath("$.data.forks[*].id", hasItem("FIX_ACTUAL_TO_CURATED")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='FIX_ACTUAL_TO_CURATED')].kind", hasItem("FIX_ACTUAL")));
    }

    @Test
    void mismatchDiagnosisIncludesFixActualAndChangeCuratedForks() throws Exception {
        MismatchFixture fx = seedAvailableRunsOnMismatch(
                "diag-fork-a", "diag-fork-b", "app-diag-fork", "ctr-diag-fork-001");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        getDiagnosis(fx.conflictId(), GENERAL_ID)
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.forks[*].id", hasItems("FIX_ACTUAL_TO_CURATED", "CHANGE_CURATED_TO_OBSERVED")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='FIX_ACTUAL_TO_CURATED')].kind", hasItem("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')].kind", hasItem("CHANGE_CURATED")));
    }

    @Test
    void changeCuratedForkTargetsCurrentAvailableObservedHost() throws Exception {
        MismatchFixture fx = seedAvailableRunsOnMismatch(
                "diag-tgt-a", "diag-tgt-b", "app-diag-tgt", "ctr-diag-tgt-001");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        getDiagnosis(fx.conflictId(), GENERAL_ID)
                .andExpect(jsonPath(
                        "$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')].description",
                        hasItem(containsString(fx.hostB()))));
    }

    @Test
    void changeCuratedForkCopyUsesContractTerms() throws Exception {
        MismatchFixture fx = seedAvailableRunsOnMismatch(
                "diag-copy-a", "diag-copy-b", "app-diag-copy", "ctr-diag-copy-001");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, fx.conflictId(), GENERAL_ID);

        MvcResult diagnosis = getDiagnosis(fx.conflictId(), GENERAL_ID).andReturn();
        String changeCopy = forkCopy(diagnosis, "CHANGE_CURATED_TO_OBSERVED");
        assertTrue(changeCopy.contains("改理想"));
        assertTrue(changeCopy.contains("策展"));
        assertTrue(changeCopy.contains("观测"));
        assertTrue(changeCopy.contains("草案"));
        assertFalse(changeCopy.contains("以观测为准"));
        assertFalse(changeCopy.contains("裁定"));
    }

    @Test
    void absentObservationKeepsRestoreForksWithoutChangeCuratedToMissing() throws Exception {
        String hostA = createHost("diag-abs-a");
        String hostB = createHost("diag-abs-b");
        String containerId = createContainer("app-diag-abs", "ctr-diag-abs-001");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"agent-diag-abs",
                                  "hostId":"%s",
                                  "snapshot":{
                                    "containers":[],
                                    "absentObjectIds":["ctr-diag-abs-001"]
                                  }
                                }
                                """.formatted(hostB))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult warn = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.observedValue.availability", is("ABSENT")))
                .andReturn();
        String conflictId = objectMapper.readTree(warn.getResponse().getContentAsString())
                .path("data").path("id").asText();

        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        MvcResult diagnosis = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.forks[*].id", hasItem("RESTORE_OBSERVATION_OR_RECREATE")))
                .andExpect(jsonPath("$.data.forks[?(@.id=='RESTORE_OBSERVATION_OR_RECREATE')].kind", hasItem("RESTORE_CHANNEL")))
                .andExpect(jsonPath("$.data.forks[?(@.kind=='CHANGE_CURATED')]").isEmpty())
                .andExpect(jsonPath("$.data.forks[?(@.id=='CHANGE_CURATED_TO_OBSERVED')]").isEmpty())
                .andReturn();

        String allCopy = objectMapper.readTree(diagnosis.getResponse().getContentAsString())
                .path("data").path("forks").toString();
        assertFalse(allCopy.contains("策展改为不存在"));
        assertFalse(allCopy.contains("改为不存在"));
    }

    @Test
    void sensitiveBusinessReadIsRejectedNotApprovalGated() throws Exception {
        mockMvc.perform(post("/api/workbench/sensitive-reads")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"business_db.orders","intent":"READ_CUSTOMER_ORDERS"}
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("SENSITIVE_BUSINESS_READ_DENIED")));
    }

    private MismatchFixture seedAvailableRunsOnMismatch(
            String hostAName, String hostBName, String containerName, String objectId) throws Exception {
        String hostA = createHost(hostAName);
        String hostB = createHost(hostBName);
        String containerId = createContainer(containerName, objectId);
        confirmRunsOn(containerId, hostA);
        heartbeatWithContainer(hostB, "agent-" + objectId, objectId);
        MvcResult warn = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andReturn();
        String conflictId = objectMapper.readTree(warn.getResponse().getContentAsString())
                .path("data").path("id").asText();
        return new MismatchFixture(conflictId, hostA, hostB, containerId);
    }

    private ResultActions getDiagnosis(String conflictId, String userId) throws Exception {
        return mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
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

    private record MismatchFixture(String conflictId, String hostA, String hostB, String containerId) {
    }

    private String forkCopy(MvcResult diagnosis, String forkId) throws Exception {
        JsonNode forks = objectMapper.readTree(diagnosis.getResponse().getContentAsString())
                .path("data").path("forks");
        for (JsonNode fork : forks) {
            if (forkId.equals(fork.path("id").asText())) {
                return String.join(" ",
                        fork.path("label").asText(),
                        fork.path("hypothesis").asText(),
                        fork.path("description").asText());
            }
        }
        throw new AssertionError("Fork not found: " + forkId);
    }
}
