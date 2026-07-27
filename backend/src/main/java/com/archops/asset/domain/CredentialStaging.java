package com.archops.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credential_staging")
public class CredentialStaging {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "temp_ref", length = 64)
    private String tempRef;

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

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getTempRef() { return tempRef; }
    public void setTempRef(String tempRef) { this.tempRef = tempRef; }
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
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
