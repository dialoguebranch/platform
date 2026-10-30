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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mock External Variable Service accepts a variables call only with a valid Keycloak token
 * for it, with the required role (#428): checked with real signed tokens against the real
 * security chain, the public keys served by a local stand-in for Keycloak.
 */
@SpringBootTest(properties = "dlb.data-dir=build/test-data")
@AutoConfigureMockMvc
class TokenCheckTest {

	private static final String ISSUER = "http://keycloak.test/realms/dialoguebranch";
	private static final String AUDIENCE = "dlb-variable-service";
	private static final RSAKey KEY = generateKey("issuer-key");
	private static final RSAKey OTHER_KEY = generateKey("issuer-key");
	private static final HttpServer KEYCLOAK = serveKeys();

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void auth(DynamicPropertyRegistry registry) {
		registry.add("dlb.auth.issuer-uri", () -> ISSUER);
		registry.add("dlb.auth.jwk-set-uri",
				() -> "http://127.0.0.1:" + KEYCLOAK.getAddress().getPort() + "/certs");
	}

	@AfterAll
	static void stopKeycloak() {
		KEYCLOAK.stop(0);
	}

	@Test
	void aValidTokenWithTheRoleIsAccepted() throws Exception {
		mockMvc.perform(supported(token(KEY, ISSUER, AUDIENCE, List.of("evs-access"), 300)))
				.andExpect(status().isOk());
	}

	@Test
	void theSupportedVariablesCarryTheirHints() throws Exception {
		// Not about the token check, but this class already sets up a valid token (#433).
		mockMvc.perform(supported(token(KEY, ISSUER, AUDIENCE, List.of("evs-access"), 300)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("currentDate"))
				.andExpect(jsonPath("$[0].type").value("string"))
				.andExpect(jsonPath("$[0].readOnly").value(true))
				.andExpect(jsonPath("$[0].values").doesNotExist())
				.andExpect(jsonPath("$[2].name").value("dayPart"))
				.andExpect(jsonPath("$[2].values[3]").value("night"))
				.andExpect(jsonPath("$[3].name").value("example.number"))
				.andExpect(jsonPath("$[3].type").value("number"))
				.andExpect(jsonPath("$[3].readOnly").value(false))
				.andExpect(jsonPath("$[4].type").value("boolean"))
				.andExpect(jsonPath("$[5].values[0]").value("a"))
				.andExpect(jsonPath("$[6].name").value("example.noHints"))
				.andExpect(jsonPath("$[6].type").doesNotExist())
				.andExpect(jsonPath("$[6].values").doesNotExist())
				.andExpect(jsonPath("$[6].readOnly").doesNotExist());
	}

	@Test
	void onlySupportedVariablesGetAValue() throws Exception {
		// Not about the token check, but this class already sets up a valid token.
		mockMvc.perform(post("/v1/variables/retrieve-updates")
						.param("subject", "s").param("timeZone", "Europe/Amsterdam")
						.header("Authorization", "Bearer "
								+ token(KEY, ISSUER, AUDIENCE, List.of("evs-access"), 300))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								[{"name": "currentDate"}, {"name": "example.number"},
								 {"name": "example.flag"}, {"name": "example.choice"},
								 {"name": "example.noHints"}, {"name": "notSupported", "value": "x"}]
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[*].name", not(hasItem("notSupported"))))
				.andExpect(jsonPath("$[1].name").value("example.number"))
				.andExpect(jsonPath("$[1].value").isNumber())
				.andExpect(jsonPath("$[2].value").isBoolean())
				.andExpect(jsonPath("$[3].value", is(oneOf("a", "b", "c"))))
				.andExpect(jsonPath("$[4].value").isString());
	}

	@Test
	void aRequestWithoutATokenIsRefused() throws Exception {
		mockMvc.perform(get("/v1/variables/supported").param("projectSlug", "p"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenForAnotherServiceIsRefused() throws Exception {
		mockMvc.perform(supported(token(KEY, ISSUER, "dlb-web-service", List.of("evs-access"),
						300)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenFromAnotherIssuerIsRefused() throws Exception {
		mockMvc.perform(supported(token(KEY, "http://elsewhere.test/realms/x", AUDIENCE,
						List.of("evs-access"), 300)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anExpiredTokenIsRefused() throws Exception {
		mockMvc.perform(supported(token(KEY, ISSUER, AUDIENCE, List.of("evs-access"), -300)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
		mockMvc.perform(supported(token(OTHER_KEY, ISSUER, AUDIENCE, List.of("evs-access"), 300)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenWithoutTheRoleIsForbidden() throws Exception {
		mockMvc.perform(supported(token(KEY, ISSUER, AUDIENCE, List.of("other-role"), 300)))
				.andExpect(status().isForbidden());
	}

	@Test
	void theInfoEndPointStaysOpen() throws Exception {
		mockMvc.perform(get("/v1/info/all")).andExpect(status().isOk());
	}

	private static MockHttpServletRequestBuilder supported(String token) {
		return get("/v1/variables/supported").param("projectSlug", "p")
				.header("Authorization", "Bearer " + token);
	}

	private static String token(RSAKey key, String issuer, String audience, List<String> roles,
			long expiresInSeconds) throws JOSEException {
		long now = System.currentTimeMillis();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject("service-account-dlb-web-service-evs")
				.audience(audience)
				.issueTime(new Date(now - 600_000))
				.expirationTime(new Date(now + expiresInSeconds * 1000))
				.claim("azp", "dlb-web-service-evs")
				.claim("resource_access", Map.of(AUDIENCE, Map.of("roles", roles)))
				.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private static RSAKey generateKey(String keyId) {
		try {
			return new RSAKeyGenerator(2048).keyID(keyId).generate();
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}

	private static HttpServer serveKeys() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			byte[] keys = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			server.createContext("/certs", exchange -> {
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, keys.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(keys);
				}
			});
			server.start();
			return server;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
