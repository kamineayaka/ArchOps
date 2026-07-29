package com.archops.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.common.bootstrap.PlatformSecrets;
import com.archops.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "unit-test-jwt-secret-must-be-at-least-32-bytes!!",
                60_000L,
                3_600_000L);
        provider = new JwtTokenProvider(properties, new PlatformSecrets(properties.secret(), "unused-master-key"));
    }

    @Test
    void accessAndRefreshTokensCarryClaims() {
        String access = provider.createAccessToken(1L, "admin", "sess-1");
        String refresh = provider.createRefreshToken(1L, "admin", "sess-1");

        Claims accessClaims = provider.parseClaims(access);
        Claims refreshClaims = provider.parseClaims(refresh);

        assertEquals("1", accessClaims.getSubject());
        assertEquals("admin", accessClaims.get("username", String.class));
        assertEquals("sess-1", accessClaims.get("sessionId", String.class));
        assertTrue(provider.isAccessToken(accessClaims));
        assertFalse(provider.isRefreshToken(accessClaims));
        assertTrue(provider.isRefreshToken(refreshClaims));
        assertFalse(provider.isAccessToken(refreshClaims));
        assertEquals(60_000L, provider.getAccessTokenExpirationMs());
    }

    @Test
    void rejectsShortSecret() {
        JwtProperties shortSecret = new JwtProperties("too-short", 1000L, 1000L);
        assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(shortSecret, new PlatformSecrets(shortSecret.secret(), "key")));
    }
}
