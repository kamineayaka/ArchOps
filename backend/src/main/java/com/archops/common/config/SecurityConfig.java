package com.archops.common.config;

import com.archops.common.security.ApiAccessDeniedHandler;
import com.archops.common.security.ApiAuthenticationEntryPoint;
import com.archops.user.security.TempAuthHeaderFilter;
import com.archops.user.service.UserLookupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    TempAuthHeaderFilter tempAuthHeaderFilter(
            UserLookupService userLookupService,
            ApiAuthenticationEntryPoint authenticationEntryPoint
    ) {
        return new TempAuthHeaderFilter(userLookupService, authenticationEntryPoint);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TempAuthHeaderFilter tempAuthHeaderFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        // Host-agent ingest is a control-plane public seam (no operator identity header).
                        .requestMatchers("/api/agent/**").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(tempAuthHeaderFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
