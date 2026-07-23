package com.archops.knowledge.architecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "architecture_proposal")
public class ArchitectureProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partition_key", nullable = false, length = 128)
    private String partitionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProposalStatus status;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json", nullable = false)
    private String diffJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fact_ops", nullable = false)
    private String factOps = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String evidence = "[]";

    @Column(length = 16)
    private String risk;

    private Double confidence;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "base_version", nullable = false)
    private Long baseVersion;

    @Column(name = "related_approval_id")
    private Long relatedApprovalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPartitionKey() { return partitionKey; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
    public ProposalStatus getStatus() { return status; }
    public void setStatus(ProposalStatus status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson != null ? diffJson : "{}"; }
    public String getFactOps() { return factOps; }
    public void setFactOps(String factOps) { this.factOps = factOps != null ? factOps : "[]"; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence != null ? evidence : "[]"; }
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getBaseVersion() { return baseVersion; }
    public void setBaseVersion(Long baseVersion) { this.baseVersion = baseVersion; }
    public Long getRelatedApprovalId() { return relatedApprovalId; }
    public void setRelatedApprovalId(Long relatedApprovalId) { this.relatedApprovalId = relatedApprovalId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
