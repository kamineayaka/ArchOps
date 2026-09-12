package com.archops.curated.service;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class UnboundDraftServiceTest {

    @Test
    void hostRuntimeKeyJoinsHostAndRuntimeWithNul() {
        assertThat(UnboundDraftService.hostRuntimeKey("host-1", "runtime-a"))
                .isEqualTo("host-1\0runtime-a");
    }

    @Test
    void objectUniqueConstraintMapsToTargetAlreadyBound() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert", new RuntimeException("ERROR: duplicate key value violates unique constraint \"unbound_bind_memory_object_uq\""));

        BusinessException mapped = UnboundDraftService.bindMemoryRace(ex);

        assertThat(mapped.getCode()).isEqualTo("UNBOUND_BIND_TARGET_ALREADY_BOUND");
        assertThat(mapped.getMessage()).isEqualTo("该策展对象已由另一个现场实体绑定，不能再绑第二个");
    }

    @Test
    void hostRuntimeUniqueConstraintMapsToCandidateConsumed() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert", new RuntimeException("ERROR: duplicate key value violates unique constraint \"unbound_bind_memory_host_runtime_uq\""));

        BusinessException mapped = UnboundDraftService.bindMemoryRace(ex);

        assertThat(mapped.getCode()).isEqualTo("UNBOUND_CANDIDATE_CONSUMED");
        assertThat(mapped.getMessage()).isEqualTo("该现场实体已因绑定或新建被消费，不能再次并入");
    }
}
