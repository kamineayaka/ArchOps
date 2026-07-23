package com.archops.knowledge.architecture.repository;

import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchitecturePartitionRepository extends JpaRepository<ArchitecturePartition, Long> {
    Optional<ArchitecturePartition> findByPartitionKey(String partitionKey);
    boolean existsByPartitionKey(String partitionKey);
}
