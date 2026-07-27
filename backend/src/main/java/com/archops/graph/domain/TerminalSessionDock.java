package com.archops.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terminal_session_dock")
public class TerminalSessionDock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "element_id", nullable = false)
    private UUID elementId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "last_opened_at", nullable = false)
    private Instant lastOpenedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (lastOpenedAt == null) {
            lastOpenedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        // lastOpenedAt set explicitly by service
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public UUID getElementId() { return elementId; }
    public void setElementId(UUID elementId) { this.elementId = elementId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public Instant getLastOpenedAt() { return lastOpenedAt; }
    public void setLastOpenedAt(Instant lastOpenedAt) { this.lastOpenedAt = lastOpenedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
