package com.archops.common.config;

import com.archops.common.security.ApiAccessDeniedHandler;
import com.archops.common.security.ApiAuthenticationEntryPoint;
import com.archops.user.security.TempAuthHeaderFilter;
import com.archops.user.service.UserLookupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        // ADR-0046: 诊断选支, 批准并冻结, 草案逐条确认, 确认关闭, and 执行 are explicit actions.
                        // Removed collaboration writes are not authenticated leftovers; no handler means 404.
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/claim").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/acknowledge").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/acknowledge-and-self-appoint").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/assign-handler").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/accept-handler").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/reject-handler").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/transfer-handler").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/conflicts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/branch-selection").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/confirm-close").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/curated-drafts/open/items/*/accept").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conflicts/*/curated-drafts/open/items/*/reject").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/curated-drafts/*/items/*/accept").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/curated-drafts/*/items/*/reject").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/operation-plans/*/approve").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/operation-plans/*/start-execution").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(tempAuthHeaderFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
