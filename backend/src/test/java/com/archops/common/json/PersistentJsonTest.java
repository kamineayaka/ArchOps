package com.archops.common.json;

import com.archops.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistentJsonTest {

    private final PersistentJson json = new PersistentJson(new ObjectMapper());

    @Test
    void writeFailureMustNotBecomeEmptyObject() {
        assertThatThrownBy(() -> json.write(new BrokenWriteValue()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to write JSON")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("JSON_PROCESSING_FAILED");
    }

    /**
     * Jackson cannot serialize this; the old catch returned "{}" as if persistence succeeded.
     */
    static final class BrokenWriteValue {
        @JsonValue
        public String jsonValue() {
            throw new IllegalStateException("broken-value");
        }
    }
}
