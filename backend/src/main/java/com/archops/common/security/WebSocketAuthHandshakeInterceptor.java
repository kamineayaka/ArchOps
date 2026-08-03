package com.archops.common.security;

import com.archops.user.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates WebSocket connections via JWT token query parameter.
 * Browsers cannot set Authorization headers on WebSocket handshakes,
 * so clients pass {@code ?token=<accessToken>} instead.
 *
 * <p>Loads the real RBAC roles from the user store (same as REST JWT filter)
 * so endpoint handlers can enforce ADMIN/OPERATOR policies.
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;
    private final UserDetailsService userDetailsService;

    public WebSocketAuthHandshakeInterceptor(
            JwtTokenProvider jwtTokenProvider,
            SessionService sessionService,
            UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionService = sessionService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest http = servletRequest.getServletRequest();
        String token = http.getParameter("token");
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            if (!jwtTokenProvider.isAccessToken(claims)) {
                return false;
            }
            Long userId = Long.valueOf(claims.getSubject());
            String sessionId = claims.get("sessionId", String.class);
            if (!sessionService.isSessionValid(userId, sessionId)) {
                return false;
            }
            String username = claims.get("username", String.class);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            Set<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                    .collect(Collectors.toSet());

            AuthUserPrincipal principal = new AuthUserPrincipal(
                    userId,
                    userDetails.getUsername(),
                    userDetails.getPassword(),
                    sessionId,
                    userDetails.isEnabled(),
                    roles);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            attributes.put("userId", userId);
            attributes.put("username", username);
            attributes.put("roles", roles);
            attributes.put("principal", principal);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
