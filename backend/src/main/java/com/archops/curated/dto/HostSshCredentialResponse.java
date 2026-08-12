package com.archops.curated.dto;

import com.archops.curated.domain.HostSshSecretKind;

/**
 * Credential metadata without any secret material.
 */
public record HostSshCredentialResponse(
        String hostId,
        String connectHost,
        int connectPort,
        String username,
        HostSshSecretKind secretKind,
        boolean configured
) {
}
