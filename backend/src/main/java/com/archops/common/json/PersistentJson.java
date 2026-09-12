package com.archops.common.json;

import com.archops.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persist JSON TEXT columns fail-closed. Do not substitute "{}" / "[]" on serde failure.
 */
public class PersistentJson {

    public static final String ERROR_CODE = "JSON_PROCESSING_FAILED";

    private final ObjectMapper objectMapper;

    public PersistentJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw failClosed("Failed to write JSON", ex);
        }
    }

    private static BusinessException failClosed(String message, JsonProcessingException cause) {
        BusinessException ex = new BusinessException(ERROR_CODE, message);
        ex.initCause(cause);
        return ex;
    }
}
