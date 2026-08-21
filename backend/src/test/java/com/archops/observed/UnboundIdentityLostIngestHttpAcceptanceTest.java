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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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

    private String readDataId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }
}
