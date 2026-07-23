package com.archops.knowledge.architecture.repository;

import com.archops.knowledge.architecture.domain.ArchitectureFact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchitectureFactRepository extends JpaRepository<ArchitectureFact, Long> {
    List<ArchitectureFact> findByPartitionIdAndStatus(Long partitionId, String status);
    List<ArchitectureFact> findByPartitionIdAndAssetIdAndStatus(Long partitionId, Long assetId, String status);
    List<ArchitectureFact> findByPartitionIdAndRevisionId(Long partitionId, Long revisionId);
    long countByPartitionIdAndStatus(Long partitionId, String status);
}
