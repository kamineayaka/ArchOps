package com.archops.conflict;

import com.archops.user.security.TempAuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

final class ConflictDiagnosisWait {

    private ConflictDiagnosisWait() {
    }

    static void waitUntilReady(MockMvc mockMvc, ObjectMapper objectMapper, String conflictId, String userId)
            throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            var result = mockMvc.perform(get("/api/conflicts/{id}/diagnosis", conflictId)
                            .header(TempAuthHeaders.USER_ID, userId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                String status = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("status").asText();
                if ("READY".equals(status) || "FAILED".equals(status)) {
                    if (!"READY".equals(status)) {
                        throw new AssertionError("Diagnosis failed: " + result.getResponse().getContentAsString());
                    }
                    return;
                }
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Timed out waiting for diagnosis READY on " + conflictId);
    }
}
