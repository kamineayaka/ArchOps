package com.archops.observed.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("identity_lost_mark")
public class IdentityLostMark {

    @TableId(type = IdType.INPUT)
    private String curatedObjectId;
    private String reason;
    private Instant markedAt;
    private String sourceAgentId;
    private String sourceHostId;
    private Boolean upgradeChainPromised;

    public String getCuratedObjectId() {
        return curatedObjectId;
    }

    public void setCuratedObjectId(String curatedObjectId) {
        this.curatedObjectId = curatedObjectId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(Instant markedAt) {
        this.markedAt = markedAt;
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

    public Boolean getUpgradeChainPromised() {
        return upgradeChainPromised;
    }

    public void setUpgradeChainPromised(Boolean upgradeChainPromised) {
        this.upgradeChainPromised = upgradeChainPromised;
    }
}
