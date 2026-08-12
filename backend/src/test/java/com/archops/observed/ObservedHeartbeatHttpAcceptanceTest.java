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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 03 HTTP acceptance: agent heartbeat/snapshot → observed truth + 规范问法.
 */
@HttpAcceptanceTest
class ObservedHeartbeatHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";
    private static final String OBJECT_ID = "ctr-obs-x-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void heartbeatWithoutAuthPersistsFreshnessAndMatchedRunsOn() throws Exception {
        String hostA = createHost("obs-host-a");
        String hostB = createHost("obs-host-b");
        String containerId = createContainer("app-x", OBJECT_ID);
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "agent-b-1",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "docker-1",
                                        "name": "app-x",
                                        "labels": { "archops.object_id": "%s" }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(hostB, OBJECT_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.agentId", is("agent-b-1")))
                .andExpect(jsonPath("$.data.freshness.lastHeartbeatAt", notNullValue()))
                .andExpect(jsonPath("$.data.freshness.lastSnapshotAt", notNullValue()))
                .andExpect(jsonPath("$.data.matched", hasSize(1)))
                .andExpect(jsonPath("$.data.matched[0].observedHostId", is(hostB)))
                .andExpect(jsonPath("$.data.matched[0].objectId", is(OBJECT_ID)));

        mockMvc.perform(get("/api/agent/{agentId}/freshness", "agent-b-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.lastHeartbeatAt", notNullValue()));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.track", is("OBSERVED")))
                .andExpect(jsonPath("$.data.observedValue.availability", is("PRESENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", is(hostB)))
                .andExpect(jsonPath("$.data.observedValue.hostName", is("obs-host-b")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)))
                .andExpect(jsonPath("$.data.curatedValue.hostName", is("obs-host-a")));
    }

    @Test
    void absentObjectIdIsUsableAbsentNotHollow() throws Exception {
        String hostA = createHost("abs-host-a");
        String hostB = createHost("abs-host-b");
        String containerId = createContainer("app-gone", "ctr-gone-001");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "agent-abs",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [],
                                    "absentObjectIds": ["ctr-gone-001"]
                                  }
                                }
                                """.formatted(hostB))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.absent", hasSize(1)))
                .andExpect(jsonPath("$.data.absent[0].availability", is("ABSENT")));

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.availability", is("ABSENT")))
                .andExpect(jsonPath("$.data.observedValue.hostId", nullValue()))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));
    }

    @Test
    void unlabeledAndIdentityLostDoNotPromiseUpgradeChain() throws Exception {
        String hostB = createHost("unb-host-b");
        String containerId = createContainer("app-lost", "ctr-lost-001");
        confirmRunsOn(containerId, hostB);

        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "agent-unb",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "docker-mystery",
                                        "name": "mystery",
                                        "labels": {}
                                      },
                                      {
                                        "runtimeId": "docker-unknown",
                                        "name": "other",
                                        "labels": { "archops.object_id": "never-curated" }
                                      }
                                    ],
                                    "identityLostObjectIds": ["ctr-lost-001"]
                                  }
                                }
                                """.formatted(hostB))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unbound", hasSize(2)))
                .andExpect(jsonPath("$.data.unbound[0].upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.unbound[1].upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.identityLost", hasSize(1)))
                .andExpect(jsonPath("$.data.identityLost[0].upgradeChainPromised", is(false)));

        mockMvc.perform(get("/api/observed/unbound-candidates")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].upgradeChainPromised", is(false)));

        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upgradeChainPromised", is(false)))
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));
    }

    @Test
    void actualWhereWithoutObservationIsHollowWithCuratedOnScreen() throws Exception {
        String hostA = createHost("hollow-host-a");
        String containerId = createContainer("app-hollow", "ctr-hollow-001");
        confirmRunsOn(containerId, hostA);

        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observedValue.availability", is("HOLLOW")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostA)));
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
