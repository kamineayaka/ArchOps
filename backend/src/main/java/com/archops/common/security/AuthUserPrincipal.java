package com.archops.common.security;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String sessionId;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthUserPrincipal(
            Long userId,
            String username,
            String password,
            String sessionId,
            boolean enabled,
            Collection<String> roles) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.sessionId = sessionId;
        this.enabled = enabled;
        this.authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }

    public Long getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    /** Role names without the {@code ROLE_} prefix (e.g. {@code ADMIN}, {@code OPERATOR}). */
    public java.util.Set<String> roleNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth != null && auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
