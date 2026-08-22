package com.archops.curated.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("curated_draft_event")
public class CuratedDraftEvent {

    @TableId(type = IdType.INPUT)
    private String id;
    private String draftId;
    private CuratedDraftEventType eventType;
    private String actorUserId;
    private String detailJson;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDraftId() { return draftId; }
    public void setDraftId(String draftId) { this.draftId = draftId; }
    public CuratedDraftEventType getEventType() { return eventType; }
    public void setEventType(CuratedDraftEventType eventType) { this.eventType = eventType; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
