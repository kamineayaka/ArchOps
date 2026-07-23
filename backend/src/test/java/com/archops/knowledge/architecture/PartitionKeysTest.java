package com.archops.knowledge.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class PartitionKeysTest {

    @Test
    void globalConstant() {
        assertThat(PartitionKeys.GLOBAL).isEqualTo("global");
    }

    @Test
    void groupAndAssetKeys() {
        assertThat(PartitionKeys.group(12L)).isEqualTo("group:12");
        assertThat(PartitionKeys.asset(7L)).isEqualTo("asset:7");
    }

    @Test
    void validateAcceptsCanonicalKeys() {
        PartitionKeys.validate("global");
        PartitionKeys.validate("group:1");
        PartitionKeys.validate("asset:99");
    }

    @Test
    void validateRejectsInvalid() {
        assertThatThrownBy(() -> PartitionKeys.validate(""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_PARTITION_KEY");
        assertThatThrownBy(() -> PartitionKeys.validate("foo"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_PARTITION_KEY");
        assertThatThrownBy(() -> PartitionKeys.validate("group:x"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_PARTITION_KEY");
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> PartitionKeys.group(0L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_ID");
        assertThatThrownBy(() -> PartitionKeys.asset(null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_ID");
    }
}
