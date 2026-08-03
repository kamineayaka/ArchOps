package com.archops.knowledge.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archops.common.exception.BusinessException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AssetAclServiceTest {

    private UserAssetRepository repository;
    private AssetAclService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAssetRepository.class);
        service = new AssetAclService(repository);
    }

    @Test
    void adminIsUnrestricted() {
        assertTrue(service.isAdmin(Set.of("ADMIN")));
        assertTrue(service.canAccessAsset(9L, Set.of("ADMIN"), 42L));
        assertEquals(List.of(1L, 2L), service.filterAssetIds(9L, Set.of("ROLE_ADMIN"), List.of(1L, 2L)));
    }

    @Test
    void nonAdminRequiresMembership() {
        when(repository.existsByUserIdAndAssetId(7L, 42L)).thenReturn(true);
        when(repository.existsByUserIdAndAssetId(7L, 99L)).thenReturn(false);
        assertTrue(service.canAccessAsset(7L, Set.of("OPERATOR"), 42L));
        assertFalse(service.canAccessAsset(7L, Set.of("OPERATOR"), 99L));
        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.requireAssetAccess(7L, Set.of("OPERATOR"), 99L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("ASSET_ACCESS_DENIED", ex.getCode());
    }

    @Test
    void grantIsIdempotent() {
        when(repository.existsByUserIdAndAssetId(3L, 10L)).thenReturn(false);
        service.grant(3L, 10L);
        verify(repository).save(org.mockito.ArgumentMatchers.any(UserAsset.class));
    }
}
