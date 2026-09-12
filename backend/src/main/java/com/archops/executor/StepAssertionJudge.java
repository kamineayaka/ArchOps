package com.archops.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Engine-side 步骤断言: JSON object key/value containment of 工具结构化结果.
 */
public final class StepAssertionJudge {

    public static final String FAILED_PREFIX = "STEP_ASSERTION_FAILED";

    private StepAssertionJudge() {
    }

    public static boolean hasExpected(Map<String, String> expected) {
        return expected != null && !expected.isEmpty();
    }

    /**
     * @return {@code null} when {@code structuredOutput} is a JSON object that contains every
     *         expected string pair; otherwise a {@code failure_reason} starting with
     *         {@link #FAILED_PREFIX}.
     */
    public static String mismatchReason(String structuredOutput, Map<String, String> expected, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(structuredOutput == null ? "" : structuredOutput);
        } catch (JsonProcessingException ex) {
            return FAILED_PREFIX + ": structured_output is not a JSON object";
        }
        if (root == null || !root.isObject()) {
            return FAILED_PREFIX + ": structured_output is not a JSON object";
        }
        if (expected == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            JsonNode value = root.get(entry.getKey());
            if (value == null || !value.isTextual() || !entry.getValue().equals(value.asText())) {
                return FAILED_PREFIX + ": structured_output does not contain "
                        + entry.getKey() + "=" + entry.getValue();
            }
        }
        return null;
    }
}
