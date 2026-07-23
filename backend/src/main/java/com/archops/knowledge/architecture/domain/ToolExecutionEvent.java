package com.archops.knowledge.architecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tool_execution_event")
public class ToolExecutionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments_json", nullable = false)
    private String argumentsJson = "{}";

    @Column(name = "stdout_summary", columnDefinition = "text")
    private String stdoutSummary;

    @Column(name = "stderr_summary", columnDefinition = "text")
    private String stderrSummary;

    @Column(name = "exit_code")
    private Integer exitCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_ids", nullable = false)
    private List<Long> assetIds = new ArrayList<>();

    @Column(length = 8)
    private String level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson != null ? argumentsJson : "{}";
    }
    public String getStdoutSummary() { return stdoutSummary; }
    public void setStdoutSummary(String stdoutSummary) { this.stdoutSummary = stdoutSummary; }
    public String getStderrSummary() { return stderrSummary; }
    public void setStderrSummary(String stderrSummary) { this.stderrSummary = stderrSummary; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public List<Long> getAssetIds() { return assetIds; }
    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds != null ? assetIds : new ArrayList<>();
    }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Instant getCreatedAt() { return createdAt; }
}
