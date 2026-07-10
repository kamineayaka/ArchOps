package com.cloudops.common.security;

import com.cloudops.common.bootstrap.PlatformSecrets;
import com.cloudops.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties, PlatformSecrets platformSecrets) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(resolveSecret(platformSecrets.jwtSecret()));
    }

    public String createAccessToken(Long userId, String username, String sessionId) {
        return buildToken(userId, username, sessionId, "access", jwtProperties.accessTokenExpirationMs());
    }

    public String createRefreshToken(Long userId, String username, String sessionId) {
        return buildToken(userId, username, sessionId, "refresh", jwtProperties.refreshTokenExpirationMs());
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type", String.class));
    }

    public long getAccessTokenExpirationMs() {
        return jwtProperties.accessTokenExpirationMs();
    }

    private String buildToken(Long userId, String username, String sessionId, String type, long expirationMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "username", username,
                        "sessionId", sessionId,
                        "type", type))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    private byte[] resolveSecret(String secret) {
        if (secret.startsWith("base64:")) {
            return Decoders.BASE64.decode(secret.substring("base64:".length()));
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits");
        }
        return bytes;
    }
}
