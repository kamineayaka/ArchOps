package com.archops.user;

import com.archops.support.HttpAcceptanceTest;
import com.archops.user.security.TempAuthHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 01 HTTP acceptance: temporary identity header → role gates.
 */
@HttpAcceptanceTest
class TempAuthHttpAcceptanceTest {

    private static final String SENIOR_ID = "user-senior-demo";
    private static final String GENERAL_ID = "user-general-demo";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthRemainsPublicWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UP")));
    }

    @Test
    void meWithoutHeaderReturnsAuthRequiredEnvelope() throws Exception {
        mockMvc.perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void meWithUnknownUserIdReturnsMappingFailedEnvelope() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(TempAuthHeaders.USER_ID, "user-does-not-exist")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("AUTH_MAPPING_FAILED")))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void meWithSeniorHeaderResolvesStableIdentityAndRole() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(SENIOR_ID)))
                .andExpect(jsonPath("$.data.displayName", is("演示主管")))
                .andExpect(jsonPath("$.data.role", is("SENIOR")))
                .andExpect(jsonPath("$.data.roleLabel", is("高级角色")));
    }

    @Test
    void meWithGeneralHeaderResolvesGeneralRole() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(GENERAL_ID)))
                .andExpect(jsonPath("$.data.role", is("GENERAL")))
                .andExpect(jsonPath("$.data.roleLabel", is("一般角色")));
    }

    @Test
    void seniorProbeAllowsSeniorAndRejectsGeneral() throws Exception {
        mockMvc.perform(get("/api/auth/probes/senior")
                        .header(TempAuthHeaders.USER_ID, SENIOR_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.allowed", is(true)))
                .andExpect(jsonPath("$.data.role", is("SENIOR")));

        mockMvc.perform(get("/api/auth/probes/senior")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    void authenticatedProbeAllowsBothRolesAndRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/probes/authenticated")
                        .header(TempAuthHeaders.USER_ID, GENERAL_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed", is(true)));

        mockMvc.perform(get("/api/auth/probes/authenticated")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")));
    }
}
