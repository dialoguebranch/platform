/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dialoguebranch.web.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the Dialogue Branch Web Service.
 *
 * <p>In native mode (dlb.auth.service=native) all requests are permitted at the filter layer —
 * authentication is handled manually inside each controller via {@link QueryRunner}.</p>
 *
 * <p>In Keycloak mode (dlb.auth.service=keycloak) the OAuth2 resource server JWT filter
 * validates every bearer token against the Keycloak JWKS endpoint before the request reaches
 * a controller. The /auth/login and /auth/refresh endpoints are always open.</p>
 *
 * @author Dennis Hofs
 */
@Configuration
public class SecurityConfig {

    private final DlbProperties dlbProperties;

    public SecurityConfig(DlbProperties dlbProperties) {
        this.dlbProperties = dlbProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (dlbProperties.getAuth().getService().equals(DlbProperties.AUTH_SERVICE_KEYCLOAK)) {
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                        "/auth/login", "/v*/auth/login",
                        "/auth/refresh", "/v*/auth/refresh",
                        "/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs/**", "/api-docs/**",
                        "/webjars/**",
                        "/actuator/health", "/actuator/info"
                    ).permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .bearerTokenResolver(new XAuthTokenBearerTokenResolver())
                    .jwt(jwt -> {})
                );
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    /**
     * Creates the {@link JwtDecoder} used to validate Keycloak-issued JWT tokens. Only registered
     * when Keycloak authentication is active. The decoder fetches and caches the public keys from
     * the Keycloak JWKS endpoint automatically.
     */
    @Bean
    @ConditionalOnProperty(name = "dlb.auth.service", havingValue = "keycloak")
    public JwtDecoder keycloakJwtDecoder() {
        DlbProperties.Auth.Keycloak kc = dlbProperties.getAuth().getKeycloak();
        String base = kc.getBaseUrl().endsWith("/") ? kc.getBaseUrl() : kc.getBaseUrl() + "/";
        String jwkSetUri = base + "realms/" + kc.getRealm() + "/protocol/openid-connect/certs";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(dlbProperties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Accept", "Accept-Language",
            "X-Requested-With", "X-Auth-Token", "ngrok-skip-browser-warning", "User-Agent"
        ));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static class XAuthTokenBearerTokenResolver implements BearerTokenResolver {
        @Override
        public String resolve(HttpServletRequest request) {
            String token = request.getHeader("X-Auth-Token");
            if (token != null && !token.isBlank()) return token;
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String stripped = auth.substring(7);
                if (!stripped.isBlank()) return stripped;
            }
            return null;
        }
    }
}
