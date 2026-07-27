package com.archops.asset.repository;

import com.archops.asset.domain.CredentialStaging;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialStagingRepository extends JpaRepository<CredentialStaging, UUID> {
    Optional<CredentialStaging> findByIdAndConsumedAtIsNull(UUID id);
}
