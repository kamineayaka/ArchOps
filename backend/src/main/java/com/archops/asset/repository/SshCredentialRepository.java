package com.archops.asset.repository;

import com.archops.asset.domain.SshCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SshCredentialRepository extends JpaRepository<SshCredential, Long> {
    Optional<SshCredential> findByAssetIdAndDeletedAtIsNull(Long assetId);
}
