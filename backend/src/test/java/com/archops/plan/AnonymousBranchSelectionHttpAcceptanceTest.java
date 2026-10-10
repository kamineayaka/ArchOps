package com.archops.plan;

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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046 ticket 01: 诊断选支 needs no user identity and records no operator.
 */
@HttpAcceptanceTest
class AnonymousBranchSelectionHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fixActualWithoutIdentityOrHandlerOpensReviewPlan() throws Exception {
        String conflictId = openConflictWithoutHandler("abs-a", "abs-b", "ctr-abs-001");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        MvcResult created = mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.skipsDraft", is(true)))
                .andExpect(jsonPath("$.data.branchKind", is("FIX_ACTUAL")))
                .andExpect(jsonPath("$.data.createdBy", nullValue()))
                .andReturn();
        String planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(get("/api/operation-plans/{id}", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdBy", nullValue()));
    }

    @Test
    void fixActualWithUserHeaderStillOmitsCreatedBy() throws Exception {
        String conflictId = openConflictWithoutHandler("abs-h-a", "abs-h-b", "ctr-abs-002");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DRAFT_REVIEW")))
                .andExpect(jsonPath("$.data.createdBy", nullValue()));
    }

    @Test
    void changeCuratedWithoutIdentityOpensDraftAndRecordsNoActor() throws Exception {
        String conflictId = openConflictWithSiblingWithoutHandler(
                "abs-c-a", "abs-c-b", "ctr-abs-003", "ctr-abs-003-y");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"CHANGE_CURATED_TO_OBSERVED\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OPEN")))
                .andExpect(jsonPath("$.data.origin", is("CHANGE_CURATED")))
                .andExpect(jsonPath("$.data.createdBy", nullValue()));

        mockMvc.perform(get("/api/conflicts/{id}/operation-plans/active", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")));

        mockMvc.perform(get("/api/conflicts/{id}/events", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='DRAFT_CREATED')].actorUserId", everyItem(nullValue())));
    }

    @Test
    void secondFixActualSelectIsRejectedWhilePlanActive() throws Exception {
        String conflictId = openConflictWithoutHandler("abs-2-a", "abs-2-b", "ctr-abs-004");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        selectFixActual(conflictId).andExpect(status().isOk());

        selectFixActual(conflictId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PLAN_ALREADY_ACTIVE")));
    }

    @Test
    void staleDiagnosisIdIsRejectedWithoutHandler() throws Exception {
        String conflictId = openConflictWithoutHandler("abs-st-a", "abs-st-b", "ctr-abs-005");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        String staleDiagnosisId = readDiagnosisId(conflictId);
        String hostC = createHost("abs-st-c");
        heartbeatWithContainer(hostC, "agent-abs-st-c", "ctr-abs-005");
        waitUntilDiagnosisReplaced(conflictId, staleDiagnosisId);

        mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\",\"diagnosisId\":\"" + staleDiagnosisId + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("DIAGNOSIS_NOT_READY")));
    }

    @Test
    void approveWithoutAcceptedHandlerFreezesPlan() throws Exception {
        String conflictId = openConflictWithoutHandler("abs-ap-a", "abs-ap-b", "ctr-abs-006");
        ConflictDiagnosisWait.waitUntilReady(mockMvc, objectMapper, conflictId, GENERAL_ID);
        MvcResult created = selectFixActual(conflictId)
                .andExpect(status().isOk())
                .andReturn();
        String planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/operation-plans/{id}/approve", planId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.reviewedBy", nullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions selectFixActual(String conflictId) throws Exception {
        return mockMvc.perform(post("/api/conflicts/{id}/branch-selection", conflictId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forkId\":\"FIX_ACTUAL_TO_CURATED\"}")
                .accept(MediaType.APPLICATION_JSON));
    }

    private String readDiagnosisId(String conflictId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private void waitUntilDiagnosisReplaced(String conflictId, String previousDiagnosisId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                            .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                String id = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("id").asText();
                String status = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("status").asText();
                if ("READY".equals(status) && !previousDiagnosisId.equals(id)) {
                    return;
                }
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Timed out waiting for a new READY diagnosis on " + conflictId);
    }

    private String openConflictWithSiblingWithoutHandler(
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
        MvcResult result = mockMvc.perform(get("/api/conflicts/by-merge-key")
                        .param("subjectId", containerX)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
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
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
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
}
