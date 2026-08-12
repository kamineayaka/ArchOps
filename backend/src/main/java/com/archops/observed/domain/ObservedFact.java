package com.archops.observed.domain;

import com.archops.curated.domain.CuratedRelationType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("observed_fact")
public class ObservedFact {

    @TableId(type = IdType.INPUT)
    private String id;
    private String subjectId;
    private CuratedRelationType relationType;
    private ObservedAvailability availability;
    private String targetId;
    private Instant observedAt;
    private String sourceAgentId;
    private String sourceHostId;

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

    public ObservedAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(ObservedAvailability availability) {
        this.availability = availability;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public String getSourceAgentId() {
        return sourceAgentId;
    }

    public void setSourceAgentId(String sourceAgentId) {
        this.sourceAgentId = sourceAgentId;
    }

    public String getSourceHostId() {
        return sourceHostId;
    }

    public void setSourceHostId(String sourceHostId) {
        this.sourceHostId = sourceHostId;
    }
}
