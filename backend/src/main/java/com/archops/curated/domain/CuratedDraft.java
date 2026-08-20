package com.archops.curated.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("curated_draft")
public class CuratedDraft {

    @TableId(type = IdType.INPUT)
    private String id;
    private String conflictId;
    private String diagnosisId;
    private String selectedForkId;
    private CuratedDraftStatus status;
    private String createdBy;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConflictId() {
        return conflictId;
    }

    public void setConflictId(String conflictId) {
        this.conflictId = conflictId;
    }

    public String getDiagnosisId() {
        return diagnosisId;
    }

    public void setDiagnosisId(String diagnosisId) {
        this.diagnosisId = diagnosisId;
    }

    public String getSelectedForkId() {
        return selectedForkId;
    }

    public void setSelectedForkId(String selectedForkId) {
        this.selectedForkId = selectedForkId;
    }

    public CuratedDraftStatus getStatus() {
        return status;
    }

    public void setStatus(CuratedDraftStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
