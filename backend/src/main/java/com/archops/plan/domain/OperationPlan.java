package com.archops.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("operation_plan")
public class OperationPlan {

    @TableId(type = IdType.INPUT)
    private String id;
    private String conflictId;
    private String diagnosisId;
    private String selectedForkId;
    private PlanBranchKind branchKind;
    private Boolean skipsDraft;
    private OperationPlanStatus status;
    private String stepsJson;
    private String createdBy;
    private Instant createdAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private Instant approvedAt;

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

    public PlanBranchKind getBranchKind() {
        return branchKind;
    }

    public void setBranchKind(PlanBranchKind branchKind) {
        this.branchKind = branchKind;
    }

    public Boolean getSkipsDraft() {
        return skipsDraft;
    }

    public void setSkipsDraft(Boolean skipsDraft) {
        this.skipsDraft = skipsDraft;
    }

    public OperationPlanStatus getStatus() {
        return status;
    }

    public void setStatus(OperationPlanStatus status) {
        this.status = status;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
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

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
