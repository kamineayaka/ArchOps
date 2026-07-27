package com.archops.asset.repository;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByKind(AssetKind kind);

    List<Asset> findByDeletedAtIsNull();

    Optional<Asset> findByIdAndDeletedAtIsNull(Long id);

    Optional<Asset> findByElementIdAndDeletedAtIsNull(UUID elementId);

    Optional<Asset> findByElementId(UUID elementId);
}
