package com.archops.observed.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("probe_task")
public class ProbeTask {

    @TableId(type = IdType.INPUT)
    private String id;
    private String hostId;
    private String command;
    private String expectedJson;
    private int intervalSeconds;
    private Instant dueAt;
    private boolean awaitingResult;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getExpectedJson() {
        return expectedJson;
    }

    public void setExpectedJson(String expectedJson) {
        this.expectedJson = expectedJson;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public boolean getAwaitingResult() {
        return awaitingResult;
    }

    public void setAwaitingResult(boolean awaitingResult) {
        this.awaitingResult = awaitingResult;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
