package com.archops.knowledge.acl;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAssetRepository extends JpaRepository<UserAsset, UserAsset.UserAssetId> {

    List<UserAsset> findByUserId(Long userId);

    boolean existsByUserIdAndAssetId(Long userId, Long assetId);

    @Query("select ua.assetId from UserAsset ua where ua.userId = :userId")
    List<Long> findAssetIdsByUserId(@Param("userId") Long userId);

    @Query("select ua.assetId from UserAsset ua where ua.userId = :userId and ua.assetId in :assetIds")
    List<Long> findAssetIdsByUserIdAndAssetIdIn(
            @Param("userId") Long userId, @Param("assetIds") Collection<Long> assetIds);
}
