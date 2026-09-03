package com.dialoguebranch.web.bff;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The BFF's hand-wired {@link SecurityConfig} filter chain: how it responds to authenticated and
 * unauthenticated requests, the {@code /api/v1/info/all} carve-out, the SPA CSRF cookie recipe,
 * and RP-initiated logout. Studio's whole login flow depends on the "plain 401, not a redirect"
 * behaviour for {@code /api/**} and {@code /whoami}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BffSecurityFilterChainTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ClientRegistrationRepository clientRegistrationRepository;

	// Present so the context wires; the filter-chain tests here do not reach a controller that
	// uses it.
	@MockitoBean
	OAuth2AuthorizedClientManager authorizedClientManager;

	// --- unauthenticated: 401 for XHR paths, 302 for a real navigation ---

	@Test
	void unauthenticatedApiCallIsA401NotARedirect() throws Exception {
		mockMvc.perform(get("/api/v1/dialogue/foo"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void unauthenticatedWhoAmiIsA401NotARedirect() throws Exception {
		mockMvc.perform(get("/whoami"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void unauthenticatedTopLevelNavigationRedirectsToTheAuthorizationEndpoint() throws Exception {
		mockMvc.perform(get("/some/app/page").header("Accept", "text/html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/oauth2/authorization/keycloak"));
	}

	// --- the /api/v1/info/all carve-out (the forwarded call itself is covered by
	//     ApiProxyControllerTest; here we only assert the authorization rule) ---

	@Test
	void nonGetInfoAllStillRequiresASession() throws Exception {
		// GET /api/v1/info/all is the only permitAll /api path; POST falls to anyRequest().
		// A raw XSRF cookie echo (what SpaCsrfTokenRequestHandler accepts) clears the CSRF filter
		// so the 401 from the authorization layer isn't masked by a 403.
		Cookie xsrf = firstRequestXsrfCookie();
		mockMvc.perform(post("/api/v1/info/all").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anyOtherApiPathRequiresASession() throws Exception {
		mockMvc.perform(get("/api/v1/info/technical"))
				.andExpect(status().isUnauthorized());
	}

	// --- always-open infrastructure paths ---

	@Test
	void actuatorHealthIsPermitted() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	void errorPathIsNotBouncedToLogin() throws Exception {
		mockMvc.perform(get("/error"))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(302))
				.andExpect(header().doesNotExist("Location"));
	}

	// --- CSRF: SPA cookie recipe ---

	@Test
	void theXsrfTokenCookieIsWrittenAndIsNotHttpOnly() throws Exception {
		Cookie xsrf = mockMvc.perform(get("/actuator/health")).andReturn()
				.getResponse().getCookie("XSRF-TOKEN");

		assertThat(xsrf).as("XSRF-TOKEN cookie").isNotNull();
		assertThat(xsrf.isHttpOnly()).as("XSRF-TOKEN must be JS-readable").isFalse();
		assertThat(xsrf.getValue()).isNotBlank();
	}

	@Test
	void aStateChangingRequestWithNoCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/logout"))
				.andExpect(status().isForbidden());
	}

	@Test
	void csrfPassesWhenTheCookieValueIsEchoedInTheHeader() throws Exception {
		Cookie xsrf = firstRequestXsrfCookie();

		mockMvc.perform(post("/logout").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void csrfPassesWhenTheCookieValueIsEchoedInTheFormParam() throws Exception {
		Cookie xsrf = firstRequestXsrfCookie();

		mockMvc.perform(post("/logout").cookie(xsrf).param("_csrf", xsrf.getValue()))
				.andExpect(status().is3xxRedirection());
	}

	// --- RP-initiated logout: an authenticated OIDC session logs out via Keycloak ---

	@Test
	void logoutRedirectsToKeycloaksEndSessionEndpoint() throws Exception {
		Cookie xsrf = firstRequestXsrfCookie();

		mockMvc.perform(post("/logout")
						.with(oidcLogin().clientRegistration(
								clientRegistrationRepository.findByRegistrationId("keycloak")))
						.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
						.startsWith("http://localhost:8081/realms/dialoguebranch/"
								+ "protocol/openid-connect/logout"));
	}

	private Cookie firstRequestXsrfCookie() throws Exception {
		Cookie xsrf = mockMvc.perform(get("/actuator/health")).andReturn()
				.getResponse().getCookie("XSRF-TOKEN");
		assertThat(xsrf).isNotNull();
		return xsrf;
	}
}
