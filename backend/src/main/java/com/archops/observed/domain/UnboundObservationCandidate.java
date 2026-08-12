package com.archops.observed.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("unbound_observation_candidate")
public class UnboundObservationCandidate {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sourceAgentId;
    private String sourceHostId;
    private String runtimeId;
    private String name;
    private String labelsJson;
    private UnboundReason reason;
    private Boolean upgradeChainPromised;
    private Instant observedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getRuntimeId() {
        return runtimeId;
    }

    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabelsJson() {
        return labelsJson;
    }

    public void setLabelsJson(String labelsJson) {
        this.labelsJson = labelsJson;
    }

    public UnboundReason getReason() {
        return reason;
    }

    public void setReason(UnboundReason reason) {
        this.reason = reason;
    }

    public Boolean getUpgradeChainPromised() {
        return upgradeChainPromised;
    }

    public void setUpgradeChainPromised(Boolean upgradeChainPromised) {
        this.upgradeChainPromised = upgradeChainPromised;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }
}
