package com.archops.asset.repository;

import com.archops.asset.domain.SshCredential;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SshCredentialRepository extends JpaRepository<SshCredential, Long> {
    Optional<SshCredential> findByAssetIdAndDeletedAtIsNull(Long assetId);

    @Query("""
            select c.assetId from SshCredential c
            where c.deletedAt is null and c.assetId in :assetIds
            """)
    List<Long> findAssetIdsWithActiveCredential(@Param("assetIds") Collection<Long> assetIds);
}
