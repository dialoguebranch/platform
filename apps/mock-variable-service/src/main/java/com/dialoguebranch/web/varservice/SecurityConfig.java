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

package com.dialoguebranch.web.varservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Checks the access token on every variables call, as a real External Variable Service must
 * (#428): the Dialogue Branch Web Service sends a Keycloak token it got with the
 * client-credentials grant. A request is refused with {@code 401} without a token, or with one
 * that is not signed by the issuer's keys, comes from another issuer, has expired or is meant for
 * another service (its {@code aud} claim lacks {@code dlb.auth.audience}); and with {@code 403}
 * when the token lacks the role {@code dlb.auth.required-role} in {@code
 * resource_access.<audience>.roles}. The info and Swagger end-points stay open.
 *
 * @author Harm op den Akker
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/**
	 * Instances of this class are constructed through Spring.
	 */
	public SecurityConfig() { }

	/**
	 * The security filter chain: a token with the required role for the variables end-points,
	 * nothing for the rest.
	 *
	 * @param http the {@link HttpSecurity} to configure.
	 * @param properties the service's configuration.
	 * @return the filter chain.
	 * @throws Exception if the chain cannot be built.
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			DlbVarServiceProperties properties) throws Exception {
		DlbVarServiceProperties.Auth auth = properties.getAuth();
		JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
		authenticationConverter.setJwtGrantedAuthoritiesConverter(roles(auth.getAudience()));
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/v*/variables/**", "/variables/**")
						.hasAuthority(auth.getRequiredRole())
						.anyRequest().permitAll())
				.oauth2ResourceServer(server -> server.jwt(jwt ->
						jwt.jwtAuthenticationConverter(authenticationConverter)));
		return http.build();
	}

	/**
	 * Decodes a token with the issuer's public keys, and checks it with {@link #tokenValidator}.
	 *
	 * @param properties the service's configuration.
	 * @return the decoder.
	 */
	@Bean
	public JwtDecoder jwtDecoder(DlbVarServiceProperties properties) {
		DlbVarServiceProperties.Auth auth = properties.getAuth();
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(auth.getJwkSetUri()).build();
		decoder.setJwtValidator(tokenValidator(auth.getIssuerUri(), auth.getAudience()));
		return decoder;
	}

	/**
	 * Accepts a token from the given issuer that has not expired, with {@code audience} in its
	 * {@code aud} claim.
	 *
	 * @param issuer the expected issuer.
	 * @param audience the client id of this service, which must be in the audience.
	 * @return the validator.
	 */
	static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
		return new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer),
				new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
						aud -> aud != null && aud.contains(audience)));
	}

	/**
	 * Reads the token's roles for this service, Keycloak's {@code
	 * resource_access.<audience>.roles}, as authorities.
	 *
	 * @param audience the client id of this service.
	 * @return the converter.
	 */
	static Converter<Jwt, Collection<GrantedAuthority>> roles(String audience) {
		return jwt -> {
			Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
			if (resourceAccess == null
					|| !(resourceAccess.get(audience) instanceof Map<?, ?> client)
					|| !(client.get("roles") instanceof Collection<?> roles)) {
				return List.of();
			}
			return roles.stream()
					.map(role -> (GrantedAuthority) new SimpleGrantedAuthority(String.valueOf(role)))
					.toList();
		};
	}
}
