package com.archops.knowledge.architecture.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archops.audit.service.AuditService;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import com.archops.knowledge.architecture.dto.FactCreateRequest;
import com.archops.knowledge.architecture.dto.RevisionWriteRequest;
import com.archops.knowledge.architecture.repository.ArchitectureFactRepository;
import com.archops.knowledge.architecture.repository.ArchitecturePartitionRepository;
import com.archops.knowledge.architecture.repository.ArchitectureRevisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchitecturePartitionServiceTest {

    @Mock
    private ArchitecturePartitionRepository partitionRepository;
    @Mock
    private ArchitectureRevisionRepository revisionRepository;
    @Mock
    private ArchitectureFactRepository factRepository;
    @Mock
    private AssetAclService assetAclService;
    @Mock
    private AuditService auditService;

    private ArchitecturePartitionService service;

    @BeforeEach
    void setUp() {
        service = new ArchitecturePartitionService(
                partitionRepository,
                revisionRepository,
                factRepository,
                assetAclService,
                auditService,
                new ObjectMapper());
    }

    @Test
    void adminWriteRejectsVersionConflict() {
        ArchitecturePartition partition = new ArchitecturePartition();
        partition.setId(1L);
        partition.setPartitionKey("global");
        partition.setHighImpact(true);

        ArchitectureRevision latest = new ArchitectureRevision();
        latest.setId(10L);
        latest.setPartitionId(1L);
        latest.setVersion(3L);

        when(partitionRepository.findByPartitionKey("global")).thenReturn(Optional.of(partition));
        when(revisionRepository.findTopByPartitionIdOrderByVersionDesc(1L)).thenReturn(Optional.of(latest));

        RevisionWriteRequest request = new RevisionWriteRequest(
                "summary", "body", "{}", 2L, List.of());

        assertThatThrownBy(() -> service.adminWrite("global", request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ARCHITECTURE_VERSION_CONFLICT");

        verify(revisionRepository, never()).save(any());
        verify(factRepository, never()).save(any());
    }

    @Test
    void adminWriteRejectsEmptyProvenance() {
        ArchitecturePartition partition = new ArchitecturePartition();
        partition.setId(1L);
        partition.setPartitionKey("asset:5");
        partition.setHighImpact(false);

        ArchitectureRevision latest = new ArchitectureRevision();
        latest.setId(10L);
        latest.setPartitionId(1L);
        latest.setVersion(1L);

        when(partitionRepository.findByPartitionKey("asset:5")).thenReturn(Optional.of(partition));
        when(revisionRepository.findTopByPartitionIdOrderByVersionDesc(1L)).thenReturn(Optional.of(latest));

        FactCreateRequest fact = new FactCreateRequest(
                "ROLE", "host-a", "IS", "namenode", 5L, 0.95, "{}");
        RevisionWriteRequest request = new RevisionWriteRequest(
                "summary", "body", "{}", 1L, List.of(fact));

        assertThatThrownBy(() -> service.adminWrite("asset:5", request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PROVENANCE_REQUIRED");

        verify(revisionRepository, never()).save(any());
        verify(factRepository, never()).save(any());
    }

    @Test
    void adminWriteRejectsBlankProvenance() {
        ArchitecturePartition partition = new ArchitecturePartition();
        partition.setId(1L);
        partition.setPartitionKey("asset:5");

        ArchitectureRevision latest = new ArchitectureRevision();
        latest.setPartitionId(1L);
        latest.setVersion(0L);

        when(partitionRepository.findByPartitionKey("asset:5")).thenReturn(Optional.of(partition));
        when(revisionRepository.findTopByPartitionIdOrderByVersionDesc(1L)).thenReturn(Optional.of(latest));

        FactCreateRequest fact = new FactCreateRequest(
                "LABEL", "host-a", "HAS", "prod", 5L, 0.9, null);
        RevisionWriteRequest request = new RevisionWriteRequest(
                "summary", null, null, 0L, List.of(fact));

        assertThatThrownBy(() -> service.adminWrite("asset:5", request, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PROVENANCE_REQUIRED");
    }
}
