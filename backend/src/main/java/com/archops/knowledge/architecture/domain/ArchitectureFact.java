package com.archops.knowledge.architecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "architecture_fact")
public class ArchitectureFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partition_id", nullable = false)
    private Long partitionId;

    @Column(name = "revision_id")
    private Long revisionId;

    @Column(name = "fact_type", nullable = false, length = 32)
    private String factType;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 128)
    private String predicate;

    @Column(nullable = false, length = 512)
    private String object;

    @Column(name = "asset_id")
    private Long assetId;

    private Double confidence;

    @Column(nullable = false, length = 32)
    private String status = "active";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance_json", nullable = false)
    private String provenanceJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPartitionId() { return partitionId; }
    public void setPartitionId(Long partitionId) { this.partitionId = partitionId; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getPredicate() { return predicate; }
    public void setPredicate(String predicate) { this.predicate = predicate; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvenanceJson() { return provenanceJson; }
    public void setProvenanceJson(String provenanceJson) {
        this.provenanceJson = provenanceJson != null ? provenanceJson : "{}";
    }
    public Instant getCreatedAt() { return createdAt; }
}
