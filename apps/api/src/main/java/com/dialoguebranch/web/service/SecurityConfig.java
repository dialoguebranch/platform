/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Security configuration for the Dialogue Branch Web Service.
 *
 * <p>The service is a pure OAuth2 resource server: the JWT filter validates every bearer token
 * before the request reaches a controller. Clients obtain tokens directly from Keycloak
 * (Authorization Code + PKCE); this service never issues or refreshes tokens itself.</p>
 *
 * <p>Some hosting platforms run one Keycloak realm per client or tenant on the same Keycloak
 * instance, named as a suffix of a shared base realm (e.g. {@code Acme-North} and
 * {@code Acme-South} alongside a base {@code Acme} realm). Rather than validating against only
 * the single realm configured in {@code dlb.auth.keycloak.realm},
 * {@link #keycloakAuthenticationManagerResolver()} also trusts any realm on the same Keycloak
 * instance that follows this naming convention, so tokens from any of those realms are accepted
 * here, all sharing this same Dialogue Branch backend and database. See that method's own Javadoc
 * for what this does and does not guarantee.</p>
 *
 * <p>Trusting a realm's signing keys says nothing about <em>which client</em> in that realm a
 * token was issued to. On top of the realm check, {@link AzpClaimValidator} matches each token's
 * {@code azp} claim against {@code dlb.auth.keycloak.trusted-clients} (defaulting to this service's
 * own client id plus the bundled BFF's {@code dlb-bff}, since Studio always reaches this service
 * through the BFF). A {@code "*"} entry in that list turns the client check off again.</p>
 *
 * @author Dennis Hofs
 */
@Configuration
public class SecurityConfig {

	private final DlbProperties dlbProperties;

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param dlbProperties the bound {@code dlb.*} configuration, used for the configured CORS
	 *                      allowed origins and Keycloak connection details.
	 */
	public SecurityConfig(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
		// QueryRunner is a static utility and cannot inject DlbProperties, so seed it here (once,
		// during context startup, before any request is handled) with the configured client id
		// whose resource_access entry carries this service's roles. See issue #104.
		QueryRunner.setResourceAccessClientId(
				dlbProperties.getAuth().getKeycloak().getClientId());
	}

	/**
	 * Configures the security filter chain for the service: stateless sessions (no server-side
	 * session state, since authentication is via bearer token on every request), CORS using
	 * {@link #corsConfigurationSource()}, CSRF protection disabled (not applicable to a
	 * stateless token-based API), a fixed set of end-points that are publicly accessible without
	 * a token, and OAuth2 JWT validation (via {@link #keycloakAuthenticationManagerResolver()})
	 * for everything else.
	 *
	 * @param http the {@link HttpSecurity} to configure.
	 * @return the configured {@link SecurityFilterChain}.
	 * @throws Exception if the security configuration cannot be built.
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/", "/index.html",
					"/info/all", "/v*/info/all",
					"/swagger-ui/**", "/swagger-ui.html",
					"/v3/api-docs/**", "/api-docs/**",
					"/webjars/**",
					"/actuator/health", "/actuator/info"
				).permitAll()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2
				.authenticationManagerResolver(keycloakAuthenticationManagerResolver())
			);

		return http.build();
	}

	/**
	 * Resolves each request's {@link AuthenticationManager} from its own JWT's {@code iss} claim,
	 * rather than validating against one fixed realm: the configured {@code dlb.auth.keycloak.realm}
	 * is trusted, and so is any realm on the same Keycloak instance named
	 * {@code <that realm>-<anything>}, a common convention for a hosting platform that provisions
	 * one Keycloak realm per client or tenant alongside its own base/admin realm. Deliberately not
	 * {@code JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(Predicate)}, which would do
	 * OIDC discovery against the token's own {@code iss} claim, letting attacker-controlled token
	 * content pick which network address this service calls out to. Instead, only the realm-name
	 * path segment is taken from the (still unverified at this point) issuer claim.
	 *
	 * <p>The realm name is extracted by matching against {@code dlb.auth.keycloak.browser-base-url},
	 * not {@code base-url}: a token's {@code iss} always reflects whichever address the browser
	 * used to reach Keycloak during login, which in a containerized deployment is typically not the
	 * same address this service itself uses to reach Keycloak internally (see
	 * {@link DlbProperties.Auth.Keycloak#getBrowserBaseUrl()}'s own Javadoc). The actual JWKS
	 * network call, by contrast, always goes to this service's own trusted {@code base-url}, with
	 * only the realm name (never a full URL) taken from the token.
	 *
	 * <p>Each per-issuer {@link NimbusJwtDecoder} validates signature, expiry and the exact issuer
	 * ({@link JwtValidators#createDefaultWithIssuer}), plus an {@link AzpClaimValidator} that
	 * matches the token's {@code azp} claim against
	 * {@link DlbProperties.Auth.Keycloak#getEffectiveTrustedClients()} — so being signed by a
	 * trusted realm is necessary but not sufficient; the token must also have been issued to a
	 * client this service is configured to trust. The single {@code AzpClaimValidator} is shared
	 * across every per-issuer decoder (the trusted-client list does not vary by realm).
	 *
	 * <p><strong>What this does not do:</strong> this service has no notion of tenant lifecycle
	 * beyond Keycloak itself, so it cannot tell a realm the hosting platform currently considers
	 * active apart from one it has deactivated elsewhere but not deleted from Keycloak. Any realm
	 * matching the naming convention above stays trusted here for as long as it exists in
	 * Keycloak. A hosting platform that needs to revoke a tenant's access to dialogue features
	 * more strictly than that should also enforce it at whatever layer of its own tracks tenant
	 * lifecycle, upstream of this service.
	 *
	 * @return the per-issuer {@link AuthenticationManagerResolver} used for OAuth2 JWT validation.
	 */
	@Bean
	public AuthenticationManagerResolver<HttpServletRequest> keycloakAuthenticationManagerResolver() {
		DlbProperties.Auth.Keycloak kc = dlbProperties.getAuth().getKeycloak();
		String internalRealmsBase = normalizeBaseUrl(kc.getBaseUrl()) + "realms/";
		String issuerRealmsBase = normalizeBaseUrl(kc.getBrowserBaseUrl()) + "realms/";
		String adminRealm = kc.getRealm();
		AzpClaimValidator azpValidator = new AzpClaimValidator(kc.getEffectiveTrustedClients());
		Map<String, AuthenticationManager> managerCache = new ConcurrentHashMap<>();

		return new JwtIssuerAuthenticationManagerResolver(issuer -> {
			String realmName = extractRealmName(issuer, issuerRealmsBase);
			if (realmName == null || !isTrustedRealm(realmName, adminRealm)) {
				return null;
			}
			return managerCache.computeIfAbsent(issuer, iss -> {
				String jwkSetUri = internalRealmsBase + realmName + "/protocol/openid-connect/certs";
				NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
				OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
						JwtValidators.createDefaultWithIssuer(iss), azpValidator);
				decoder.setJwtValidator(validator);
				JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
				return (AuthenticationManager) authentication -> provider.authenticate(authentication);
			});
		});
	}

	private static String normalizeBaseUrl(String baseUrl) {
		return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
	}

	/**
	 * Extracts the single realm-name path segment from {@code issuer} if it starts with
	 * {@code realmsBase} and names exactly one further segment (no extra {@code /}); {@code null}
	 * otherwise (issuer from an unrelated Keycloak instance, or a malformed value).
	 *
	 * <p>Package-private, not private, so {@code SecurityConfigTest} can exercise the trust
	 * decision directly without needing to construct a signed JWT and a servlet request.
	 */
	static String extractRealmName(String issuer, String realmsBase) {
		if (issuer == null || !issuer.startsWith(realmsBase)) {
			return null;
		}
		String remainder = issuer.substring(realmsBase.length());
		if (remainder.isEmpty() || remainder.contains("/")) {
			return null;
		}
		return remainder;
	}

	/** True for {@code adminRealm} itself, or any {@code <adminRealm>-<slug>} tenant realm name. */
	static boolean isTrustedRealm(String realmName, String adminRealm) {
		return realmName.equals(adminRealm) || realmName.startsWith(adminRealm + "-");
	}

	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(dlbProperties.getCors().getAllowedOrigins());
		config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of(
			"Authorization", "Content-Type", "Accept", "Accept-Language",
			"X-Requested-With", "ngrok-skip-browser-warning", "User-Agent"
		));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
