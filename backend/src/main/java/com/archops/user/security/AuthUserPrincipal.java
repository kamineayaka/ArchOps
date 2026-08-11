package com.archops.user.security;

import com.archops.user.domain.PlatformRole;
import com.archops.user.domain.PlatformUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated actor resolved from temporary identity header {@code X-ArchOps-User-Id}.
 */
public final class AuthUserPrincipal implements UserDetails {

    private final String userId;
    private final String displayName;
    private final PlatformRole role;

    public AuthUserPrincipal(String userId, String displayName, PlatformRole role) {
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
    }

    public static AuthUserPrincipal from(PlatformUser user) {
        return new AuthUserPrincipal(user.getId(), user.getDisplayName(), user.getRole());
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PlatformRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return userId;
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
        return true;
    }
}
