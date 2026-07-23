package com.archops.knowledge.architecture.repository;

import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchitectureRevisionRepository extends JpaRepository<ArchitectureRevision, Long> {
    Optional<ArchitectureRevision> findTopByPartitionIdOrderByVersionDesc(Long partitionId);
    Optional<ArchitectureRevision> findByPartitionIdAndVersion(Long partitionId, Long version);
    List<ArchitectureRevision> findByPartitionIdOrderByVersionDesc(Long partitionId);
}
