package com.archops.curated;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 02 HTTP acceptance: curated hosts / containers / 运行于 / 「应该在哪」.
 */
@HttpAcceptanceTest
class CuratedTruthHttpAcceptanceTest {

    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void curatedWritesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/curated/hosts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"host-a\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void createHostsContainerConfirmRunsOnAndAskShouldWhere() throws Exception {
        String hostAId = createHost("host-a");
        String hostBId = createHost("host-b");
        String containerId = createContainer("app-x", "ctr-x-001");

        mockMvc.perform(post("/api/curated/facts/runs-on")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"containerId":"%s","hostId":"%s"}
                                """.formatted(containerId, hostAId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.relationType", is("RUNS_ON")))
                .andExpect(jsonPath("$.data.relationLabel", is("运行于")))
                .andExpect(jsonPath("$.data.subject.id", is(containerId)))
                .andExpect(jsonPath("$.data.subject.objectId", is("ctr-x-001")))
                .andExpect(jsonPath("$.data.subject.objectLabel", is("archops.object_id=ctr-x-001")))
                .andExpect(jsonPath("$.data.target.id", is(hostAId)))
                .andExpect(jsonPath("$.data.target.name", is("host-a")))
                .andExpect(jsonPath("$.data.target.kind", is("PHYSICAL_HOST")));

        mockMvc.perform(get("/api/curated/facts/runs-on/{containerId}", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.id", is(hostAId)))
                .andExpect(jsonPath("$.data.relationLabel", is("运行于")));

        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.question", is("应该在哪")))
                .andExpect(jsonPath("$.data.track", is("CURATED")))
                .andExpect(jsonPath("$.data.relationLabel", is("运行于")))
                .andExpect(jsonPath("$.data.subject.id", is(containerId)))
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostAId)))
                .andExpect(jsonPath("$.data.curatedValue.hostName", is("host-a")));

        // host B exists for the slice story (A/B); curated fact remains on A
        mockMvc.perform(get("/api/curated/asks/should-where")
                        .param("containerId", containerId)
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedValue.hostId", is(hostAId)));

        // sanity: second host id is distinct
        org.junit.jupiter.api.Assertions.assertNotEquals(hostAId, hostBId);
    }

    @Test
    void duplicateImmutableObjectIdIsRejected() throws Exception {
        createContainer("app-x", "ctr-dup-1");
        mockMvc.perform(post("/api/curated/containers")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"app-y\",\"objectId\":\"ctr-dup-1\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CURATED_OBJECT_ID_EXISTS")));
    }

    private String createHost(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/curated/hosts")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.kind", is("PHYSICAL_HOST")))
                .andExpect(jsonPath("$.data.name", is(name)))
                .andExpect(jsonPath("$.data.id", startsWith("host-")))
                .andExpect(jsonPath("$.data.objectId", nullValue()))
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
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.kind", is("DOCKER_CONTAINER")))
                .andExpect(jsonPath("$.data.name", is(name)))
                .andExpect(jsonPath("$.data.objectId", is(objectId)))
                .andExpect(jsonPath("$.data.objectLabel", is("archops.object_id=" + objectId)))
                .andExpect(jsonPath("$.data.id", startsWith("ctr-")))
                .andReturn();
        return readDataId(result);
    }

    private String readDataId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asText();
    }
}
