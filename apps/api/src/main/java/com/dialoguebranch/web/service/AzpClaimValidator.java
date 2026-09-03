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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link OAuth2TokenValidator} that checks a Keycloak access token's {@code azp} ("authorized
 * party") claim against an explicit allow-list of trusted client IDs.
 *
 * <p>Signature, issuer and expiry validation only establish that a token was minted by a trusted
 * Keycloak realm; they say nothing about <em>which client</em> in that realm requested it. In a
 * shared-SSO deployment where several backend services live under one realm, this validator makes
 * "which clients' tokens this service accepts" an explicit, reviewable list in this service's own
 * configuration ({@code dlb.auth.keycloak.trusted-clients}), rather than an emergent property of
 * whatever clients happen to be registered in the realm.</p>
 *
 * <p>A lone {@code "*"} entry in the allow-list disables the check entirely: any token from an
 * otherwise-trusted realm is accepted, restoring the behaviour from before this validator existed.
 * {@code "*"} must be the <em>only</em> entry when present — a list that mixes it with real client
 * ids is a configuration mistake (it would silently trust everyone) and is rejected at startup.
 * A token with no {@code azp} claim is always rejected (Keycloak always populates it on access
 * tokens, so its absence is anomalous).</p>
 *
 * <p>Rejections surface as a normal {@code 401} (error code {@code invalid_token}), the same as
 * every other authentication failure. To help a self-hoster notice a misconfiguration rather than
 * chase a mystery {@code 401}, the first rejection of each distinct {@code azp} value is also
 * logged at {@code WARN}; repeats stay silent. The set that tracks which values have already been
 * logged is a bounded LRU capped at {@link #WARN_CACHE_CAP} entries, so a flood of tokens carrying
 * random {@code azp} values cannot grow it without bound. Once {@link #WARN_CACHE_CAP} distinct
 * values have been seen, newly-seen values are logged at {@code DEBUG} rather than {@code WARN}, so
 * such a flood cannot drown the log either — but repeats are still de-duplicated against the LRU
 * window regardless.</p>
 *
 * @author Harm op den Akker
 */
final class AzpClaimValidator implements OAuth2TokenValidator<Jwt> {

	/** Upper bound on the number of distinct rejected {@code azp} values remembered for WARN de-duplication. */
	static final int WARN_CACHE_CAP = 100;

	private static final Logger logger = LoggerFactory.getLogger(AzpClaimValidator.class);

	private final Set<String> trustedClients;
	private final boolean trustAnyClient;

	/**
	 * Access-ordered LRU of rejected {@code azp} values already logged, so repeats stay silent
	 * without the set growing without bound under a flood of random {@code azp} values. Bounded to
	 * {@link #WARN_CACHE_CAP} entries; guarded by {@code this} (only touched on the uncommon
	 * rejection path).
	 */
	@SuppressWarnings("serial") // anonymous LinkedHashMap subclass, never serialized
	private final Map<String, Boolean> loggedAzpValues =
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
					return size() > WARN_CACHE_CAP;
				}
			};

	/** Count of distinct {@code azp} values seen; once past {@link #WARN_CACHE_CAP}, new ones drop to DEBUG. */
	private long distinctRejectedAzpValues;

	/**
	 * Creates a validator that accepts tokens whose {@code azp} claim is one of
	 * {@code trustedClients}. If {@code trustedClients} is exactly {@code ["*"]}, every token is
	 * accepted regardless of its {@code azp}.
	 *
	 * @param trustedClients the trusted client IDs; must be non-empty (callers pass
	 *                       {@code DlbProperties.Auth.Keycloak#getEffectiveTrustedClients()},
	 *                       which never returns an empty list).
	 * @throws IllegalArgumentException if {@code "*"} appears alongside other entries — that would
	 *                                  silently trust every client, so it is treated as a
	 *                                  configuration error rather than honoured.
	 */
	AzpClaimValidator(Collection<String> trustedClients) {
		this.trustedClients = Set.copyOf(trustedClients);
		this.trustAnyClient = this.trustedClients.contains("*");
		if (trustAnyClient && this.trustedClients.size() > 1) {
			throw new IllegalArgumentException("dlb.auth.keycloak.trusted-clients: the wildcard "
					+ "\"*\" must be the only entry when present; got " + this.trustedClients);
		}
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (trustAnyClient) {
			return OAuth2TokenValidatorResult.success();
		}
		String azp = token.getClaimAsString("azp");
		if (azp != null && trustedClients.contains(azp)) {
			return OAuth2TokenValidatorResult.success();
		}
		logFirstRejection(azp);
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(
				"invalid_token",
				"The token's azp claim '" + azp + "' is not a trusted client",
				null));
	}

	/**
	 * Logs the first rejection of each distinct {@code azp} value at {@code WARN} (or {@code DEBUG}
	 * once {@link #WARN_CACHE_CAP} distinct values have been seen), and stays silent for repeats
	 * that are still within the LRU window.
	 */
	private synchronized void logFirstRejection(String azp) {
		String key = azp == null ? "<missing>" : azp;
		// put() returns the previous value (non-null) for a key already in the window, and also
		// marks it most-recently-used; a null return means this is a newly-seen value.
		if (loggedAzpValues.put(key, Boolean.TRUE) != null) {
			return;
		}
		if (distinctRejectedAzpValues++ < WARN_CACHE_CAP) {
			logger.warn("Rejected a validly-signed token from an untrusted client (azp='{}'). "
					+ "Add it to dlb.auth.keycloak.trusted-clients if this client is meant to "
					+ "call this service.", key);
		} else {
			logger.debug("Rejected a validly-signed token from an untrusted client (azp='{}').", key);
		}
	}
}
