package com.archops.curated.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("curated_draft_item")
public class CuratedDraftItem {

    @TableId(type = IdType.INPUT)
    private String id;
    private String draftId;
    private Integer seq;
    private CuratedDraftItemKind kind;
    private CuratedDraftItemStatus status;
    private String subjectId;
    private String fromHostId;
    private String toHostId;
    private String payloadJson;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = draftId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public CuratedDraftItemKind getKind() {
        return kind;
    }

    public void setKind(CuratedDraftItemKind kind) {
        this.kind = kind;
    }

    public CuratedDraftItemStatus getStatus() {
        return status;
    }

    public void setStatus(CuratedDraftItemStatus status) {
        this.status = status;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getFromHostId() {
        return fromHostId;
    }

    public void setFromHostId(String fromHostId) {
        this.fromHostId = fromHostId;
    }

    public String getToHostId() {
        return toHostId;
    }

    public void setToHostId(String toHostId) {
        this.toHostId = toHostId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
