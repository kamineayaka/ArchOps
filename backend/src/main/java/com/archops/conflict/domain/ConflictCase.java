package com.archops.conflict.domain;

import com.archops.curated.domain.CuratedRelationType;
import com.archops.observed.domain.ObservedAvailability;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("conflict_case")
public class ConflictCase {

    @TableId(type = IdType.INPUT)
    private String id;
    private String subjectId;
    private CuratedRelationType relationType;
    private ConflictStatus status;
    private String curatedTargetId;
    private ObservedAvailability observedAvailability;
    private String observedTargetId;
    private String observedLineageJson;
    private Instant firstWarnedAt;
    private Instant updatedAt;
    private Boolean acknowledged;
    private Instant acknowledgedAt;
    private String ownerUserId;
    private String handlerUserId;
    private HandlerAcceptance handlerAcceptance;
    private Instant pendingCloseAt;
    private Instant closedAt;
    private Instant suspendedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public CuratedRelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(CuratedRelationType relationType) {
        this.relationType = relationType;
    }

    public ConflictStatus getStatus() {
        return status;
    }

    public void setStatus(ConflictStatus status) {
        this.status = status;
    }

    public String getCuratedTargetId() {
        return curatedTargetId;
    }

    public void setCuratedTargetId(String curatedTargetId) {
        this.curatedTargetId = curatedTargetId;
    }

    public ObservedAvailability getObservedAvailability() {
        return observedAvailability;
    }

    public void setObservedAvailability(ObservedAvailability observedAvailability) {
        this.observedAvailability = observedAvailability;
    }

    public String getObservedTargetId() {
        return observedTargetId;
    }

    public void setObservedTargetId(String observedTargetId) {
        this.observedTargetId = observedTargetId;
    }

    public String getObservedLineageJson() {
        return observedLineageJson;
    }

    public void setObservedLineageJson(String observedLineageJson) {
        this.observedLineageJson = observedLineageJson;
    }

    public Instant getFirstWarnedAt() {
        return firstWarnedAt;
    }

    public void setFirstWarnedAt(Instant firstWarnedAt) {
        this.firstWarnedAt = firstWarnedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(Boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getHandlerUserId() {
        return handlerUserId;
    }

    public void setHandlerUserId(String handlerUserId) {
        this.handlerUserId = handlerUserId;
    }

    public HandlerAcceptance getHandlerAcceptance() {
        return handlerAcceptance;
    }

    public void setHandlerAcceptance(HandlerAcceptance handlerAcceptance) {
        this.handlerAcceptance = handlerAcceptance;
    }

    public Instant getPendingCloseAt() {
        return pendingCloseAt;
    }

    public void setPendingCloseAt(Instant pendingCloseAt) {
        this.pendingCloseAt = pendingCloseAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }
}
