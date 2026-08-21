package com.archops.observed;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 01 HTTP acceptance: unbound labels, upsert, inferred 身份失联, 规范问法 projection.
 */
@HttpAcceptanceTest
class UnboundIdentityLostIngestHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unknownObjectIdUnboundCandidateListsFieldLabels() throws Exception {
        String hostId = createHost("u01a-h");

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01a-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01a-rt-unknown",
                                        "name": "u01a-unknown",
                                        "labels": { "archops.object_id": "u01a-oid" }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.unbound[0].reason", is("UNKNOWN_OBJECT_ID")));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn();

        JsonNode candidate = unboundByRuntimeId(listed, "u01a-rt-unknown");
        assertThat(candidate, notNullValue());
        assertThat(candidate.path("reason").asText(), is("UNKNOWN_OBJECT_ID"));
        assertThat(candidate.path("name").asText(), is("u01a-unknown"));
        assertThat(candidate.path("sourceHostId").asText(), is(hostId));
        assertThat(candidate.path("runtimeId").asText(), is("u01a-rt-unknown"));
        assertThat(candidate.path("upgradeChainPromised").asBoolean(), is(false));
        assertThat(candidate.path("labels").isObject(), is(true));
        assertThat(candidate.path("labels").path("archops.object_id").asText(), is("u01a-oid"));
    }

    @Test
    void sameHostAndRuntimeIdUnboundCandidateIsUpserted() throws Exception {
        String hostId = createHost("u01b-h");

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01b-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01b-rt",
                                        "name": "u01b-first",
                                        "labels": {}
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.unbound", hasSize(1)))
                .andExpect(jsonPath("$.data.unbound[0].reason", is("MISSING_LABEL")));

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01b-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01b-rt",
                                        "name": "u01b-second",
                                        "labels": { "archops.object_id": "u01b-never-curated" }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.unbound", hasSize(1)))
                .andExpect(jsonPath("$.data.unbound[0].reason", is("UNKNOWN_OBJECT_ID")));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn();

        JsonNode data = objectMapper.readTree(listed.getResponse().getContentAsString()).path("data");
        int matches = 0;
        JsonNode candidate = null;
        for (JsonNode node : data) {
            if ("u01b-rt".equals(node.path("runtimeId").asText())) {
                matches++;
                candidate = node;
            }
        }
        assertThat(matches, is(1));
        assertThat(candidate, notNullValue());
        assertThat(candidate.path("name").asText(), is("u01b-second"));
        assertThat(candidate.path("reason").asText(), is("UNKNOWN_OBJECT_ID"));
        assertThat(candidate.path("sourceHostId").asText(), is(hostId));
        assertThat(candidate.path("upgradeChainPromised").asBoolean(), is(false));
        assertThat(candidate.path("labels").path("archops.object_id").asText(), is("u01b-never-curated"));
    }

    @Test
    void curatedHostSnapshotInfersIdentityLostWithoutAgentDeclaration() throws Exception {
        String hostA = createHost("u01c-h");
        String containerId = createContainer("u01c-x", "u01c-oid");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01c-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01c-rt-miss",
                                        "name": "u01c-miss",
                                        "labels": {}
                                      }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostA))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));
    }

    @Test
    void otherHostSnapshotDoesNotMarkIdentityLost() throws Exception {
        String hostA = createHost("u01d-ha");
        String hostC = createHost("u01d-hc");
        String containerId = createContainer("u01d-x", "u01d-oid");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01d-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01d-rt",
                                        "name": "u01d-miss",
                                        "labels": {}
                                      }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostC))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("IDENTITY_LOST_NOT_FOUND")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        MvcResult listed = mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode candidate = unboundByRuntimeId(listed, "u01d-rt");
        assertThat(candidate, notNullValue());
        assertThat(candidate.path("reason").asText(), is("MISSING_LABEL"));
        assertThat(candidate.path("sourceHostId").asText(), is(hostC));
        assertThat(candidate.path("upgradeChainPromised").asBoolean(), is(false));
    }

    @Test
    void currentlyUsableObservedHostSnapshotInfersIdentityLost() throws Exception {
        String hostA = createHost("u01e-ha");
        String hostB = createHost("u01e-hb");
        String containerId = createContainer("u01e-x", "u01e-oid");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01e-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01e-rt-hit",
                                        "name": "u01e-x",
                                        "labels": { "archops.object_id": "u01e-oid" }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostB))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched[0].observedHostId", is(hostB)));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)));

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01e-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01e-rt-miss",
                                        "name": "u01e-miss",
                                        "labels": {}
                                      }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostB))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));
    }

    @Test
    void neverObservedIdentityLostActualWhereIsNotHollow() throws Exception {
        String hostA = createHost("u01f1-h");
        String containerId = createContainer("u01f1-x", "u01f1-oid");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "u01f1-ag",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "u01f1-rt-miss",
                                        "name": "u01f1-miss",
                                        "labels": {}
                                      }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(hostA))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.track", is("OBSERVED")))
                .andExpect(jsonPath("$.data.identityLost", is(true)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.observedValue.hostId", nullValue()))
                .andExpect(jsonPath("$.data.observedValue.availability", is("IDENTITY_LOST")));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));
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
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }
}
