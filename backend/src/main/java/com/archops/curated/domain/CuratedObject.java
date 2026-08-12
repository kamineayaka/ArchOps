package com.archops.curated.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("curated_object")
public class CuratedObject {

    @TableId(type = IdType.INPUT)
    private String id;
    private CuratedObjectKind kind;
    private String name;
    private String immutableObjectId;
    private String createdBy;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CuratedObjectKind getKind() {
        return kind;
    }

    public void setKind(CuratedObjectKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImmutableObjectId() {
        return immutableObjectId;
    }

    public void setImmutableObjectId(String immutableObjectId) {
        this.immutableObjectId = immutableObjectId;
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
