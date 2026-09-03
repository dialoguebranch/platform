package com.dialoguebranch.web.bff;

import jakarta.servlet.http.Cookie;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ApiProxyController} forwarding: prefix stripping, query pass-through, bearer-token
 * attachment (and its absence for the anonymous {@code info/all} path), verbatim relay of the
 * downstream status/body/headers minus the hop-by-hop ones, and the {@code IllegalStateException}
 * when there is no authorized client. The downstream Web Service is a {@link MockWebServer} on a
 * real socket, so the header handling is exercised over actual HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiProxyControllerTest {

	// Started in a static initializer, not @BeforeAll: the Spring context (and the
	// dlb.bff.api-base-url @DynamicPropertySource that reads webService.url(...)) is built before
	// @BeforeAll runs.
	private static final MockWebServer webService = new MockWebServer();

	static {
		try {
			webService.start();
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OAuth2AuthorizedClientManager authorizedClientManager;

	@AfterAll
	static void stopWebService() throws IOException {
		webService.shutdown();
	}

	@DynamicPropertySource
	static void apiBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("dlb.bff.api-base-url", () -> webService.url("/dlb-web-service").toString());
	}

	@BeforeEach
	void authorizedClientReturnsAStubToken() {
		when(authorizedClientManager.authorize(any())).thenReturn(stubAuthorizedClient());
	}

	@AfterEach
	void drainRecordedRequests() throws InterruptedException {
		while (webService.takeRequest(20, TimeUnit.MILLISECONDS) != null) {
			// A test that only asserts on the proxied response never takeRequest()s the recorded
			// one; drain so it can't be mistaken for the next test's.
		}
	}

	@Test
	void stripsTheApiPrefixAndForwardsPathPlusQueryVerbatimWithABearerToken() throws Exception {
		webService.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

		mockMvc.perform(get("/api/v1/dialogue/foo").queryParam("bar", "baz").with(user("alice")))
				.andExpect(status().isOk());

		RecordedRequest downstream = webService.takeRequest();
		assertThat(downstream.getPath()).isEqualTo("/dlb-web-service/v1/dialogue/foo?bar=baz");
		assertThat(downstream.getHeader("Authorization")).isEqualTo("Bearer stub-access-token");
	}

	@Test
	void forwardsTheAnonymousInfoAllCallWithNoAuthorizationHeader() throws Exception {
		webService.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

		mockMvc.perform(get("/api/v1/info/all")).andExpect(status().isOk());

		RecordedRequest downstream = webService.takeRequest();
		assertThat(downstream.getPath()).isEqualTo("/dlb-web-service/v1/info/all");
		assertThat(downstream.getHeader("Authorization")).isNull();
	}

	@Test
	void relaysDownstreamStatusBodyAndHeadersButNotHopByHopOnes() throws Exception {
		webService.enqueue(new MockResponse()
				.setResponseCode(418)
				.setHeader("Content-Type", "text/plain")
				.setHeader("X-Downstream", "yes")
				.addHeader("Connection", "keep-alive")
				.setBody("teapot"));

		mockMvc.perform(get("/api/v1/dialogue/foo").with(user("alice")))
				.andExpect(status().is(418))
				.andExpect(content().string("teapot"))
				.andExpect(header().string("X-Downstream", "yes"))
				.andExpect(header().doesNotExist("Connection"))
				.andExpect(header().doesNotExist("Transfer-Encoding"));
	}

	@Test
	void forwardsTheIncomingContentTypeAndAcceptHeaders() throws Exception {
		webService.enqueue(new MockResponse().setResponseCode(200));

		Cookie xsrf = xsrfCookie();
		mockMvc.perform(post("/api/v1/dialogue/start")
						.with(user("alice"))
						.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
						.contentType("application/json").accept("application/xml")
						.content("{\"x\":1}"))
				.andExpect(status().isOk());

		RecordedRequest downstream = webService.takeRequest();
		assertThat(downstream.getHeader("Content-Type")).startsWith("application/json");
		assertThat(downstream.getHeader("Accept")).isEqualTo("application/xml");
		assertThat(downstream.getBody().readUtf8()).isEqualTo("{\"x\":1}");
	}

	@Test
	void anAuthenticatedRequestWithNoAuthorizedClientIsAnError() {
		when(authorizedClientManager.authorize(any())).thenReturn(null);

		// No @ControllerAdvice covers IllegalStateException, so MockMvc propagates it.
		assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/dialogue/foo").with(user("alice"))))
				.hasRootCauseInstanceOf(IllegalStateException.class);
	}

	private Cookie xsrfCookie() throws Exception {
		Cookie xsrf = mockMvc.perform(get("/actuator/health")).andReturn()
				.getResponse().getCookie("XSRF-TOKEN");
		assertThat(xsrf).isNotNull();
		return xsrf;
	}

	private static OAuth2AuthorizedClient stubAuthorizedClient() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
				.clientId("test-bff")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.authorizationUri("http://localhost/auth")
				.tokenUri("http://localhost/token")
				.build();
		OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				"stub-access-token", Instant.now(), Instant.now().plusSeconds(300));
		return new OAuth2AuthorizedClient(registration, "alice", token);
	}
}
