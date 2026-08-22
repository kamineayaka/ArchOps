package com.archops.observed.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("unbound_bind_memory")
public class UnboundBindMemory {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sourceHostId;
    private String runtimeId;
    private String curatedObjectId;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCuratedObjectId() {
        return curatedObjectId;
    }

    public void setCuratedObjectId(String curatedObjectId) {
        this.curatedObjectId = curatedObjectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
