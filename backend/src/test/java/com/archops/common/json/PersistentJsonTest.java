package com.archops.common.json;

import com.archops.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistentJsonTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final PersistentJson json = new PersistentJson(new ObjectMapper());

    @Test
    void writeFailureMustNotBecomeEmptyObject() {
        assertFailClosed(() -> json.write(new BrokenWriteValue()), "Failed to write JSON");
    }

    @Test
    void writeFailureMustNotBecomeEmptyArray() {
        assertFailClosed(() -> json.write(List.of(new BrokenWriteValue())), "Failed to write JSON");
    }

    @Test
    void readFailureMustNotBecomeEmptyMap() {
        assertFailClosed(() -> json.read("{", MAP, Map.of()), "Failed to read JSON");
    }

    @Test
    void blankOrNullJsonIsMissingNotFailure() {
        Map<String, Object> missing = Map.of("absent", true);
        assertThat(json.read(null, MAP, missing)).isEqualTo(missing);
        assertThat(json.read("  ", MAP, missing)).isEqualTo(missing);
    }

    @Test
    void emptyCollectionsWriteCanonicalEmptyJson() {
        assertThat(json.write(Map.of())).isEqualTo("{}");
        assertThat(json.write(List.of())).isEqualTo("[]");
    }

    @Test
    void validEmptyObjectReadsAsEmptyMap() {
        assertThat(json.read("{}", MAP, Map.of("x", 1))).isEqualTo(Map.of());
    }

    private static void assertFailClosed(ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
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
