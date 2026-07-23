package com.archops.knowledge.architecture.repository;

import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchitectureProposalRepository extends JpaRepository<ArchitectureProposal, Long> {
    List<ArchitectureProposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status);
    List<ArchitectureProposal> findByPartitionKeyOrderByCreatedAtDesc(String partitionKey);
    List<ArchitectureProposal> findByStatusAndPartitionKeyOrderByCreatedAtDesc(
            ProposalStatus status, String partitionKey);
    List<ArchitectureProposal> findAllByOrderByCreatedAtDesc();
    long countByStatus(ProposalStatus status);
}
