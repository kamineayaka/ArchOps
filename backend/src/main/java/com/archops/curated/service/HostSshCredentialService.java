package com.archops.curated.service;

import com.archops.common.crypto.SecretBox;
import com.archops.common.exception.BusinessException;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.domain.HostSshCredential;
import com.archops.curated.dto.HostSshCredentialResponse;
import com.archops.curated.dto.UpsertHostSshCredentialRequest;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.curated.mapper.HostSshCredentialMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Encrypted SSH credential store for graph-resident physical hosts (ticket 08).
 * Responses never include plaintext or ciphertext.
 */
@Service
public class HostSshCredentialService {

    private final HostSshCredentialMapper credentialMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final SecretBox secretBox;

    public HostSshCredentialService(
            HostSshCredentialMapper credentialMapper,
            CuratedObjectMapper curatedObjectMapper,
            SecretBox secretBox
    ) {
        this.credentialMapper = credentialMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.secretBox = secretBox;
    }

    @Transactional
    public HostSshCredentialResponse upsert(String hostId, UpsertHostSshCredentialRequest request) {
        requirePhysicalHost(hostId);
        String secret = request.secret();
        // Encrypt immediately; do not retain plaintext beyond this method.
        String ciphertext = secretBox.encrypt(secret);

        Instant now = Instant.now();
        HostSshCredential existing = credentialMapper.selectById(hostId);
        int port = request.connectPort() == null ? 22 : request.connectPort();
        if (existing == null) {
            HostSshCredential row = new HostSshCredential();
            row.setHostId(hostId);
            row.setConnectHost(request.connectHost().trim());
            row.setConnectPort(port);
            row.setUsername(request.username().trim());
            row.setSecretCiphertext(ciphertext);
            row.setSecretKind(request.secretKind());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            credentialMapper.insert(row);
            return toResponse(row);
        }
        existing.setConnectHost(request.connectHost().trim());
        existing.setConnectPort(port);
        existing.setUsername(request.username().trim());
        existing.setSecretCiphertext(ciphertext);
        existing.setSecretKind(request.secretKind());
        existing.setUpdatedAt(now);
        credentialMapper.updateById(existing);
        return toResponse(existing);
    }

    @Transactional(readOnly = true)
    public HostSshCredentialResponse get(String hostId) {
        requirePhysicalHost(hostId);
        HostSshCredential row = credentialMapper.selectById(hostId);
        if (row == null) {
            throw new BusinessException("HOST_SSH_CREDENTIAL_NOT_FOUND",
                    "No SSH credential configured for host: " + hostId);
        }
        return toResponse(row);
    }

    /**
     * Decrypt for MINA executor only — callers must not log or return the secret.
     */
    @Transactional(readOnly = true)
    public DecryptedHostSshCredential requireDecrypted(String hostId) {
        requirePhysicalHost(hostId);
        HostSshCredential row = credentialMapper.selectById(hostId);
        if (row == null) {
            throw new BusinessException("HOST_SSH_CREDENTIAL_NOT_FOUND",
                    "No SSH credential configured for host: " + hostId);
        }
        String secret = secretBox.decrypt(row.getSecretCiphertext());
        return new DecryptedHostSshCredential(
                row.getHostId(),
                row.getConnectHost(),
                row.getConnectPort() == null ? 22 : row.getConnectPort(),
                row.getUsername(),
                secret,
                row.getSecretKind()
        );
    }

    private void requirePhysicalHost(String hostId) {
        CuratedObject host = curatedObjectMapper.selectById(hostId);
        if (host == null || host.getKind() != CuratedObjectKind.PHYSICAL_HOST) {
            throw new BusinessException("HOST_OFF_GRAPH",
                    "SSH credentials require a graph-resident physical host: " + hostId);
        }
    }

    private static HostSshCredentialResponse toResponse(HostSshCredential row) {
        return new HostSshCredentialResponse(
                row.getHostId(),
                row.getConnectHost(),
                row.getConnectPort() == null ? 22 : row.getConnectPort(),
                row.getUsername(),
                row.getSecretKind(),
                true
        );
    }

    public record DecryptedHostSshCredential(
            String hostId,
            String connectHost,
            int connectPort,
            String username,
            String secret,
            com.archops.curated.domain.HostSshSecretKind secretKind
    ) {
        @Override
        public String toString() {
            return "DecryptedHostSshCredential{hostId='%s', connectHost='%s', connectPort=%d, username='%s', secret=***, secretKind=%s}"
                    .formatted(hostId, connectHost, connectPort, username, secretKind);
        }
    }
}
