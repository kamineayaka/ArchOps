package com.archops.graph.service;

import com.archops.asset.domain.CredentialStaging;
import com.archops.asset.domain.SshAuthType;
import com.archops.asset.repository.CredentialStagingRepository;
import com.archops.common.exception.BusinessException;
import com.archops.common.security.CredentialCipher;
import com.archops.common.security.CredentialCipher.EncryptedSecret;
import com.archops.graph.config.GraphProperties;
import com.archops.graph.dto.CredentialStagingCreateRequest;
import com.archops.graph.dto.CredentialStagingResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CredentialStagingService {

    private final CredentialStagingRepository stagingRepository;
    private final CredentialCipher credentialCipher;
    private final GraphProperties graphProperties;

    public CredentialStagingService(
            CredentialStagingRepository stagingRepository,
            CredentialCipher credentialCipher,
            GraphProperties graphProperties) {
        this.stagingRepository = stagingRepository;
        this.credentialCipher = credentialCipher;
        this.graphProperties = graphProperties;
    }

    @Transactional
    public CredentialStagingResponse create(CredentialStagingCreateRequest request, Long requesterId) {
        if (!StringUtils.hasText(request.secret())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SECRET_REQUIRED", "secret 不能为空");
        }
        SshAuthType authType = request.authType() != null ? request.authType() : SshAuthType.PASSWORD;
        EncryptedSecret encrypted = credentialCipher.encrypt(request.secret());
        Instant now = Instant.now();
        CredentialStaging staging = new CredentialStaging();
        staging.setId(UUID.randomUUID());
        staging.setRequesterId(requesterId);
        staging.setUsername(request.username().trim());
        staging.setAuthType(authType);
        staging.setSecretCipher(encrypted.cipher());
        staging.setSecretIv(encrypted.iv());
        staging.setAssetId(request.assetId());
        staging.setTempRef(request.tempRef());
        staging.setExpiresAt(now.plus(graphProperties.getCredentialStagingTtl()));
        staging = stagingRepository.save(staging);
        return new CredentialStagingResponse(
                staging.getId(), staging.getExpiresAt(), staging.getTempRef(), staging.getAssetId());
    }
}
