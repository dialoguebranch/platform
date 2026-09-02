package com.dialoguebranch.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link DlbProperties.Auth.Keycloak#getEffectiveTrustedClients()}, the resolved {@code azp}
 * allow-list enforced by {@link AzpClaimValidator}.
 */
class DlbPropertiesTest {

	@Test
	void effectiveTrustedClientsDefaultsToTheClientIdPlusTheBffWhenUnset() {
		DlbProperties.Auth.Keycloak kc = new DlbProperties.Auth.Keycloak();
		kc.setClientId("dlb-web-service");
		assertEquals(List.of("dlb-web-service", "dlb-bff"), kc.getEffectiveTrustedClients());
	}

	@Test
	void effectiveTrustedClientsFollowsACustomClientIdButStillAddsTheBffWhenUnset() {
		DlbProperties.Auth.Keycloak kc = new DlbProperties.Auth.Keycloak();
		kc.setClientId("my-renamed-client");
		assertEquals(List.of("my-renamed-client", "dlb-bff"), kc.getEffectiveTrustedClients());
	}

	@Test
	void effectiveTrustedClientsIsNotDuplicatedWhenTheClientIdIsAlreadyTheBffClientId() {
		DlbProperties.Auth.Keycloak kc = new DlbProperties.Auth.Keycloak();
		kc.setClientId("dlb-bff");
		assertEquals(List.of("dlb-bff"), kc.getEffectiveTrustedClients());
	}

	@Test
	void effectiveTrustedClientsUsesTheExplicitListVerbatimWhenSet() {
		DlbProperties.Auth.Keycloak kc = new DlbProperties.Auth.Keycloak();
		kc.setClientId("dlb-web-service");
		// An explicit list replaces the default entirely — the BFF is only auto-added when unset.
		kc.setTrustedClients(List.of("dlb-web-service", "sibling-a"));
		assertEquals(List.of("dlb-web-service", "sibling-a"), kc.getEffectiveTrustedClients());
	}

	@Test
	void effectiveTrustedClientsPassesTheWildcardEntryThrough() {
		DlbProperties.Auth.Keycloak kc = new DlbProperties.Auth.Keycloak();
		kc.setTrustedClients(List.of("*"));
		assertEquals(List.of("*"), kc.getEffectiveTrustedClients());
	}
}
