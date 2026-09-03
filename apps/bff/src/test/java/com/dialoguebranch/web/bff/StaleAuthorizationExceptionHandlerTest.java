package com.dialoguebranch.web.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link StaleAuthorizationExceptionHandler}: when the session's refresh token has expired
 * Keycloak-side while the session itself is still valid, the authorized-client lookup throws
 * {@code OAuth2AuthorizationException} — which must surface as a plain 401 (the same signal an
 * unauthenticated request gets), with the session invalidated.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaleAuthorizationExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OAuth2AuthorizedClientManager authorizedClientManager;

	@Test
	void aStaleAuthorizationBecomesAPlain401() throws Exception {
		when(authorizedClientManager.authorize(any()))
				.thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));

		var result = mockMvc.perform(get("/whoami").with(user("alice")))
				.andExpect(status().isUnauthorized())
				.andReturn();

		assertThat(result.getResponse().getContentAsByteArray())
				.as("401 body should be empty").isEmpty();
	}
}
