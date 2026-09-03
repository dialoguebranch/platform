package com.dialoguebranch.web.bff;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WhoAmIController} reads {@code preferred_username} and
 * {@code resource_access.<web-service-client-id>.roles} out of the session's access token
 * (decoded, not re-validated — it came from this service's own trusted session).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WhoAmIControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OAuth2AuthorizedClientManager authorizedClientManager;

	@Test
	void returnsUsernameAndRolesFromTheAccessToken() throws Exception {
		stubAccessToken(new JWTClaimsSet.Builder()
				.claim("preferred_username", "alice")
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of("editor", "participant"))))
				.build());

		mockMvc.perform(get("/whoami").with(user("alice")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("alice"))
				.andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.containsInAnyOrder(
						"editor", "participant")));
	}

	@Test
	void aTokenWithNoResourceAccessClaimYieldsEmptyRoles() throws Exception {
		stubAccessToken(new JWTClaimsSet.Builder().claim("preferred_username", "alice").build());

		mockMvc.perform(get("/whoami").with(user("alice")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("alice"))
				.andExpect(jsonPath("$.roles").isEmpty());
	}

	@Test
	void aTokenWithNoPreferredUsernameYieldsAnEmptyUsername() throws Exception {
		stubAccessToken(new JWTClaimsSet.Builder()
				.claim("resource_access", Map.of("dlb-web-service", Map.of("roles", List.of("admin"))))
				.build());

		mockMvc.perform(get("/whoami").with(user("alice")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(""))
				.andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("admin")));
	}

	@Test
	void anUnparseableAccessTokenIsAnError() {
		when(authorizedClientManager.authorize(any()))
				.thenReturn(authorizedClientWithTokenValue("not-a-jwt"));

		// No @ControllerAdvice covers IllegalStateException, so MockMvc propagates it.
		assertThatThrownBy(() -> mockMvc.perform(get("/whoami").with(user("alice"))))
				.hasRootCauseInstanceOf(java.text.ParseException.class);
	}

	@Test
	void noAuthorizedClientIsAnError() {
		when(authorizedClientManager.authorize(any())).thenReturn(null);

		assertThatThrownBy(() -> mockMvc.perform(get("/whoami").with(user("alice"))))
				.hasRootCauseInstanceOf(IllegalStateException.class);
	}

	private void stubAccessToken(JWTClaimsSet claims) {
		when(authorizedClientManager.authorize(any()))
				.thenReturn(authorizedClientWithTokenValue(new PlainJWT(claims).serialize()));
	}

	private static OAuth2AuthorizedClient authorizedClientWithTokenValue(String tokenValue) {
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
				.clientId("test-bff")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.authorizationUri("http://localhost/auth")
				.tokenUri("http://localhost/token")
				.build();
		OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				tokenValue, Instant.now(), Instant.now().plusSeconds(300));
		return new OAuth2AuthorizedClient(registration, "alice", token);
	}
}
