package com.archops.user.security;

import com.archops.user.service.UserLookupService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Maps {@link TempAuthHeaders#USER_ID} to a persisted platform user.
 * Missing header leaves the request anonymous; unknown id fails authentication.
 */
public class TempAuthHeaderFilter extends OncePerRequestFilter {

    private final UserLookupService userLookupService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public TempAuthHeaderFilter(
            UserLookupService userLookupService,
            AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.userLookupService = userLookupService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawUserId = request.getHeader(TempAuthHeaders.USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthUserPrincipal principal = userLookupService.findById(rawUserId)
                    .map(AuthUserPrincipal::from)
                    .orElseThrow(() -> new BadCredentialsException("Unknown user id for temporary auth header"));

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            authenticationEntryPoint.commence(request, response, ex);
        }
    }
}
