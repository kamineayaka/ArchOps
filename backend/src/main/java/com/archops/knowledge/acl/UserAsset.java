package com.archops.knowledge.acl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "user_assets")
@IdClass(UserAsset.UserAssetId.class)
public class UserAsset {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    public UserAsset() {}

    public UserAsset(Long userId, Long assetId) {
        this.userId = userId;
        this.assetId = assetId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public static class UserAssetId implements Serializable {
        private Long userId;
        private Long assetId;

        public UserAssetId() {}

        public UserAssetId(Long userId, Long assetId) {
            this.userId = userId;
            this.assetId = assetId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getAssetId() {
            return assetId;
        }

        public void setAssetId(Long assetId) {
            this.assetId = assetId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UserAssetId that)) {
                return false;
            }
            return Objects.equals(userId, that.userId) && Objects.equals(assetId, that.assetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, assetId);
        }
    }
}
