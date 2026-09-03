package com.dialoguebranch.web.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code keycloak} {@link ClientRegistration} is built by hand in {@link SecurityConfig}
 * (no OIDC discovery) so that {@code end_session_endpoint} can be supplied directly. This checks
 * the endpoints it derives from {@code dlb.bff.keycloak-*} + {@code keycloak-realm}, including the
 * browser-vs-internal base-URL split.
 */
@SpringBootTest
class BffClientRegistrationTest {

	@Autowired
	ClientRegistrationRepository repository;

	@Test
	void theKeycloakRegistrationCarriesTheExpectedEndpointsAndMetadata() {
		ClientRegistration reg = repository.findByRegistrationId("keycloak");
		assertThat(reg).isNotNull();

		assertThat(reg.getClientId()).isEqualTo("test-bff");
		assertThat(reg.getClientAuthenticationMethod())
				.isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
		assertThat(reg.getAuthorizationGrantType())
				.isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
		assertThat(reg.getScopes()).contains("openid");

		ClientRegistration.ProviderDetails provider = reg.getProviderDetails();
		String realmPath = "realms/dialoguebranch/protocol/openid-connect/";
		// Authorization endpoint uses the browser-facing base URL; token + JWKS use the internal one.
		// Both are http://localhost:8081/ in the test config, so this also documents that mapping.
		assertThat(provider.getAuthorizationUri())
				.isEqualTo("http://localhost:8081/" + realmPath + "auth");
		assertThat(provider.getTokenUri())
				.isEqualTo("http://localhost:8081/" + realmPath + "token");
		assertThat(provider.getJwkSetUri())
				.isEqualTo("http://localhost:8081/" + realmPath + "certs");
		assertThat(provider.getConfigurationMetadata())
				.containsEntry("end_session_endpoint",
						"http://localhost:8081/" + realmPath + "logout");
	}
}
