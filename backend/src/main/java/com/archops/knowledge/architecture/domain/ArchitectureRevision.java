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
@Table(name = "architecture_revision")
public class ArchitectureRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partition_id", nullable = false)
    private Long partitionId;

    @Column(nullable = false)
    private Long version;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "body_md", columnDefinition = "text")
    private String bodyMd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_json", nullable = false)
    private String structuredJson = "{}";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPartitionId() { return partitionId; }
    public void setPartitionId(Long partitionId) { this.partitionId = partitionId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBodyMd() { return bodyMd; }
    public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
    public String getStructuredJson() { return structuredJson; }
    public void setStructuredJson(String structuredJson) {
        this.structuredJson = structuredJson != null ? structuredJson : "{}";
    }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
