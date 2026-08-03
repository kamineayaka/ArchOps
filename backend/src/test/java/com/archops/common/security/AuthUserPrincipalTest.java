package com.archops.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class AuthUserPrincipalTest {

    @Test
    void prefixesRolesWithRole_() {
        AuthUserPrincipal principal = new AuthUserPrincipal(
                7L, "admin", "hash", "sess", true, List.of("ADMIN", "OPERATOR"));

        assertEquals(7L, principal.getUserId());
        assertEquals("sess", principal.getSessionId());
        assertEquals("admin", principal.getUsername());
        assertTrue(principal.isEnabled());
        assertTrue(principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList()
                .containsAll(List.of("ROLE_ADMIN", "ROLE_OPERATOR")));
        assertTrue(principal.roleNames().containsAll(List.of("ADMIN", "OPERATOR")));
    }
}
