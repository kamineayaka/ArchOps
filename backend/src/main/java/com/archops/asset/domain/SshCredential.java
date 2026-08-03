package com.archops.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "ssh_credentials")
public class SshCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(nullable = false, length = 64)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 16)
    private SshAuthType authType;

    @Column(name = "secret_cipher", nullable = false)
    private byte[] secretCipher;

    @Column(name = "secret_iv", nullable = false)
    private byte[] secretIv;

    @Column(name = "passphrase_hash", length = 255)
    private String passphraseHash;

    /**
     * Legacy column — dialer reads Neo4j {@code CONNECTS_VIA} only.
     * Kept for migration importer / rollback; merges clear it to empty.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "jump_asset_ids", nullable = false)
    private List<Long> jumpAssetIds = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public SshAuthType getAuthType() { return authType; }
    public void setAuthType(SshAuthType authType) { this.authType = authType; }
    public byte[] getSecretCipher() { return secretCipher; }
    public void setSecretCipher(byte[] secretCipher) { this.secretCipher = secretCipher; }
    public byte[] getSecretIv() { return secretIv; }
    public void setSecretIv(byte[] secretIv) { this.secretIv = secretIv; }
    public String getPassphraseHash() { return passphraseHash; }
    public void setPassphraseHash(String passphraseHash) { this.passphraseHash = passphraseHash; }
    public List<Long> getJumpAssetIds() { return jumpAssetIds; }
    public void setJumpAssetIds(List<Long> jumpAssetIds) {
        this.jumpAssetIds = jumpAssetIds != null ? jumpAssetIds : new ArrayList<>();
    }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public Long getSupersededBy() { return supersededBy; }
    public void setSupersededBy(Long supersededBy) { this.supersededBy = supersededBy; }
    public boolean isDeleted() { return deletedAt != null; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
