package com.archops.curated.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("host_ssh_credential")
public class HostSshCredential {

    @TableId(type = IdType.INPUT)
    private String hostId;
    private String connectHost;
    private Integer connectPort;
    private String username;
    private String secretCiphertext;
    private HostSshSecretKind secretKind;
    private Instant createdAt;
    private Instant updatedAt;

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getConnectHost() {
        return connectHost;
    }

    public void setConnectHost(String connectHost) {
        this.connectHost = connectHost;
    }

    public Integer getConnectPort() {
        return connectPort;
    }

    public void setConnectPort(Integer connectPort) {
        this.connectPort = connectPort;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public void setSecretCiphertext(String secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
    }

    public HostSshSecretKind getSecretKind() {
        return secretKind;
    }

    public void setSecretKind(HostSshSecretKind secretKind) {
        this.secretKind = secretKind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
