package com.archops.graph.service;

import com.archops.audit.service.AuditService;
import com.archops.knowledge.architecture.domain.ArchitectureProposal;
import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.repository.ArchitectureProposalRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists proposal terminal failure/conflict outside the merge transaction. */
@Service
public class GraphProposalStatusService {

    private final ArchitectureProposalRepository proposalRepository;
    private final AuditService auditService;

    public GraphProposalStatusService(
            ArchitectureProposalRepository proposalRepository, AuditService auditService) {
        this.proposalRepository = proposalRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markConflict(Long proposalId, Long actorId, long expected, long actual) {
        ArchitectureProposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            return;
        }
        proposal.setStatus(ProposalStatus.CONFLICT);
        proposal.setConflictDetail("{\"expected\":" + expected + ",\"actual\":" + actual + "}");
        proposal.setDecidedAt(Instant.now());
        proposalRepository.save(proposal);
        auditService.record(new AuditService.AuditEntry(
                actorId,
                null,
                "graph.proposal.conflict",
                "architecture_proposal:" + proposalId,
                "HIGH",
                "SUCCESS",
                proposal.getConflictDetail(),
                null,
                null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMergeFailed(Long proposalId, Long actorId, String stage, String error) {
        ArchitectureProposal proposal = proposalRepository.findById(proposalId).orElse(null);
        if (proposal == null) {
            return;
        }
        proposal.setStatus(ProposalStatus.MERGE_FAILED);
        proposal.setConflictDetail(
                "{\"stage\":\"" + stage + "\",\"error\":\"" + sanitize(error) + "\"}");
        proposal.setDecidedAt(Instant.now());
        proposalRepository.save(proposal);
        auditService.record(new AuditService.AuditEntry(
                actorId,
                null,
                "graph.proposal.merge_failed",
                "architecture_proposal:" + proposalId,
                "HIGH",
                "FAILURE",
                proposal.getConflictDetail(),
                null,
                null));
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String cleaned = message.replace('"', '\'');
        return cleaned.substring(0, Math.min(cleaned.length(), 200));
    }
}
