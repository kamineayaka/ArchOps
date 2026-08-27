package com.archops.observed;

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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 09 HTTP acceptance: 身份失联 superimposed on heartbeat timeout still answers 观测空洞.
 */
@HttpAcceptanceTest
@TestPropertySource(properties = {
        "archops.observation.heartbeat-timeout=30s",
        "archops.observation.hollow-scan-interval-ms=3600000"
})
class IdentityLostHeartbeatTimeoutAskHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HostAgentMapper hostAgentMapper;

    @Test
    void identityLostPlusHeartbeatTimeoutScanActualWhereIsHollow() throws Exception {
        String hostA = createHost("u09a-h");
        String containerId = createContainer("u09a-x", "u09a-oid");
        confirmRunsOn(containerId, hostA);

        heartbeatUnlabeled(hostA, "u09a-ag", "u09a-rt-miss", "u09a-miss");
        assertIdentityLost(containerId);

        backdateAgent("u09a-ag");
        mockMvc.perform(post("/api/observed/scan-heartbeat-timeouts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertActualWhere(containerId, hostA, "HOLLOW", true);
        assertShouldWhere(containerId, hostA);
        assertIdentityLost(containerId);
    }

    @Test
    void identityLostWithFreshHeartbeatActualWhereStaysIdentityLost() throws Exception {
        String hostA = createHost("u09b-h");
        String containerId = createContainer("u09b-x", "u09b-oid");
        confirmRunsOn(containerId, hostA);

        heartbeatUnlabeled(hostA, "u09b-ag", "u09b-rt-miss", "u09b-miss");
        assertActualWhere(containerId, hostA, "IDENTITY_LOST", true);
        assertIdentityLost(containerId);
    }

    private void heartbeatUnlabeled(String hostId, String agentId, String runtimeId, String name) throws Exception {
        mockMvc.perform(post("/api/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "hostId": "%s",
                                  "snapshot": {
                                    "containers": [
                                      {
                                        "runtimeId": "%s",
                                        "name": "%s",
                                        "labels": {}
                                      }
                                    ],
                                    "absentObjectIds": []
                                  }
                                }
                                """.formatted(agentId, hostId, runtimeId, name))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void backdateAgent(String agentId) {
        hostAgentMapper.update(null, new LambdaUpdateWrapper<HostAgent>()
                .eq(HostAgent::getAgentId, agentId)
                .set(HostAgent::getLastHeartbeatAt, Instant.now().minus(2, ChronoUnit.MINUTES)));
    }

    private void assertActualWhere(
            String containerId,
            String curatedHostId,
            String availability,
            boolean identityLost
    ) throws Exception {
        mockMvc.perform(get("/api/observed/asks/actual-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.question", is("实际在哪")))
                .andExpect(jsonPath("$.data.track", is("OBSERVED")))
                .andExpect(jsonPath("$.data.identityLost", is(identityLost)))
                .andExpect(jsonPath("$.data.observedValue.availability", is(availability)))
                .andExpect(jsonPath("$.data.observedValue.hostId", nullValue()))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(curatedHostId)));
    }

    private void assertShouldWhere(String containerId, String curatedHostId) throws Exception {
        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(curatedHostId)));
    }

    private void assertIdentityLost(String containerId) throws Exception {
        mockMvc.perform(get("/api/observed/identity-lost/{id}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.reason", is("LABEL_CLUE_LOST")));
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
