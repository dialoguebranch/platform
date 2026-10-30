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

package com.dialoguebranch.web.service.storage;

import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.controller.schema.SupportedVariableInfo;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalVariableServiceClientTest {

	private FakeExternalVariableService service;

	@AfterEach
	void stopService() {
		if (service != null) service.stop();
	}

	private static ExternalVariableServiceClient clientFor(FakeExternalVariableService service,
			Duration readTimeout) {
		return new ExternalVariableServiceClient(propertiesFor(service, readTimeout));
	}

	private static DlbProperties propertiesFor(FakeExternalVariableService service,
			Duration readTimeout) {
		DlbProperties properties = new DlbProperties();
		DlbProperties.ExternalVariableService evs = properties.getExternalVariableService();
		service.configure(evs);
		evs.setConnectTimeout(Duration.ofMillis(500));
		evs.setReadTimeout(readTimeout);
		return properties;
	}

	/** A client whose tokens come from a working authorization server, at an unreachable EVS. */
	private ExternalVariableServiceClient clientForUnreachableService() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ZERO);
		DlbProperties properties = propertiesFor(service, Duration.ofSeconds(1));
		properties.getExternalVariableService().setUrl(unreachableUrl());
		return new ExternalVariableServiceClient(properties);
	}

	private static String unreachableUrl() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return "http://127.0.0.1:" + socket.getLocalPort();
		}
	}

	private static List<Variable> oneVariable() {
		return List.of(new Variable("steps", 10, ZonedDateTime.now(ZoneId.of("UTC")),
				VariableUpdatedSource.DLB_SCRIPT));
	}

	@Test
	void throwsServiceUnavailableWhenExternalVariableServiceCannotBeReached() throws IOException {
		ExternalVariableServiceClient client = clientForUnreachableService();

		ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class,
				() -> client.getSupportedVariables("default-test"));

		assertEquals(ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
				exception.getError().getCode());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
				ServiceUnavailableException.class.getAnnotation(ResponseStatus.class).value());
	}

	@Test
	void supportedVariablesCarryTheServicesHintsAndOnlyThose() throws Exception {
		service = FakeExternalVariableService.answering(200, """
				[{"name": "visits.count", "description": "How often the user has visited",
				  "type": "number", "readOnly": true, "unit": "visits"},
				 {"name": "settings.theme", "type": "string",
				  "values": ["light", "dark"], "readOnly": false},
				 {"name": "mood"}]
				""", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		List<SupportedVariableInfo> supported = client.getSupportedVariables("p");

		assertEquals(new SupportedVariableInfo("visits.count", "How often the user has visited", "number",
				null, true), supported.get(0));
		assertEquals(new SupportedVariableInfo("settings.theme", null, "string",
				List.of("light", "dark"), false), supported.get(1));
		assertEquals(new SupportedVariableInfo("mood", null, null, null, null), supported.get(2));
		// A hint the service left out stays absent on the way to Studio.
		assertEquals("{\"name\":\"mood\"}", new ObjectMapper().writeValueAsString(supported.get(2)));
	}

	@Test
	void anUnreachableServiceNeverBreaksTheCaller() throws IOException {
		ExternalVariableServiceClient client = clientForUnreachableService();

		assertEquals(0, client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false)
				.length);
		assertDoesNotThrow(() -> client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable()));
		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
	}

	@Test
	void aServiceThatAnswersWithAnErrorNeverBreaksTheCaller() throws IOException {
		service = FakeExternalVariableService.answering(500, "", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		assertEquals(0, client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false)
				.length);
		assertDoesNotThrow(() -> client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable()));
		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
		assertThrows(ServiceUnavailableException.class, () -> client.getSupportedVariables("p"));
	}

	@Test
	void aSlowServiceIsCutOffByTheReadTimeout() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ofSeconds(3));
		ExternalVariableServiceClient client = clientFor(service, Duration.ofMillis(300));

		long start = System.nanoTime();
		Variable[] updates = client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		assertEquals(0, updates.length);
		assertTrue(elapsedMs < 2000, "retrieve-updates waited " + elapsedMs + " ms");
	}

	@Test
	void retrieveUpdatesSendsTheIdentityAndReturnsTheUpdates() throws IOException {
		service = FakeExternalVariableService.answering(200,
				"[{\"name\":\"steps\",\"value\":42,\"updatedTime\":0,\"timeZone\":\"UTC\"}]",
				Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		Variable[] updates = client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), true);

		assertEquals(1, updates.length);
		assertEquals("steps", updates[0].getName());
		FakeExternalVariableService.Request request = service.requests().get(0);
		assertEquals("/v1/variables/retrieve-updates", request.path());
		assertTrue(request.query().contains("subject=sub"), request.query());
		assertTrue(request.query().contains("testSession=true"), request.query());
		assertEquals("Bearer token-1", request.authorization());
	}

	@Test
	void notifyUpdatedSendsTheChangedVariables() throws IOException {
		service = FakeExternalVariableService.answering(200, "", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable());

		FakeExternalVariableService.Request request = service.requests().get(0);
		assertEquals("/v1/variables/notify-updated", request.path());
		assertTrue(request.body().contains("\"steps\""), request.body());
	}

	@Test
	void anUpdateIsStoredAsUpdatedWhenItWasReceived() {
		ZoneId zone = ZoneId.of("Europe/Amsterdam");
		VariableStore store = new VariableStore(new User("sub", zone));
		store.setValue("gone", "old", false, ZonedDateTime.now(zone), VariableUpdatedSource.UNKNOWN);
		long before = System.currentTimeMillis();

		ExternalVariableServiceClient.applyUpdates(store, new Variable[] {
				new Variable("steps", 42, 0L, "UTC", VariableUpdatedSource.UNKNOWN),
				new Variable("gone", null, 0L, "UTC", VariableUpdatedSource.UNKNOWN)});

		Variable steps = store.getVariable("steps");
		assertEquals(42, steps.getValue());
		assertTrue(steps.getUpdatedTime() >= before, "updatedTime " + steps.getUpdatedTime());
		assertEquals("Europe/Amsterdam", steps.getUpdatedTimeZone());
		assertEquals(VariableUpdatedSource.EXTERNAL, steps.getUpdatedSource());
		assertNull(store.getVariable("gone"));
	}

	@Test
	void theTokenIsFetchedWithTheClientCredentialsAndReused() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false);
		client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable());
		client.notifyCleared("iss", "sub", "p", "UTC");

		assertEquals(1, service.tokenRequests().size());
		FakeExternalVariableService.Request tokenRequest = service.tokenRequests().get(0);
		assertTrue(tokenRequest.body().contains("grant_type=client_credentials"),
				tokenRequest.body());
		assertEquals("Basic " + Base64.getEncoder().encodeToString(
				"dlb-web-service-evs:test-secret".getBytes(StandardCharsets.UTF_8)),
				tokenRequest.authorization());
		assertEquals(3, service.requests().size());
		for (FakeExternalVariableService.Request request : service.requests()) {
			assertEquals("Bearer token-1", request.authorization());
		}
	}

	@Test
	void aTokenAboutToExpireIsFetchedAgain() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ZERO);
		service.tokenLifetimeSeconds = 30;
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		client.notifyCleared("iss", "sub", "p", "UTC");
		client.notifyCleared("iss", "sub", "p", "UTC");

		assertEquals(2, service.tokenRequests().size());
		assertEquals("Bearer token-2", service.requests().get(1).authorization());
	}

	@Test
	void aRejectedTokenIsDiscarded() throws IOException {
		service = FakeExternalVariableService.answering(401, "", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
		client.notifyCleared("iss", "sub", "p", "UTC");

		assertEquals("Bearer token-1", service.requests().get(0).authorization());
		assertEquals("Bearer token-2", service.requests().get(1).authorization());
	}

	@Test
	void anAuthorizationServerThatFailsNeverBreaksTheCaller() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ZERO);
		service.tokenStatus = 401;
		ExternalVariableServiceClient client = clientFor(service, Duration.ofSeconds(1));

		assertEquals(0, client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false)
				.length);
		assertDoesNotThrow(() -> client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable()));
		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
		assertThrows(ServiceUnavailableException.class, () -> client.getSupportedVariables("p"));
		assertEquals(0, service.requests().size());
	}

	@Test
	void anUnreachableAuthorizationServerNeverBreaksTheCaller() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ZERO);
		DlbProperties properties = propertiesFor(service, Duration.ofSeconds(1));
		properties.getExternalVariableService().getOauth2().setTokenUri(unreachableUrl());
		ExternalVariableServiceClient client = new ExternalVariableServiceClient(properties);

		assertEquals(0, client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false)
				.length);
		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
		assertEquals(0, service.requests().size());
	}

	@Test
	void missingOAuth2SettingsStopTheServiceAtStartup() {
		DlbProperties properties = new DlbProperties();
		DlbProperties.ExternalVariableService evs = properties.getExternalVariableService();
		evs.setEnabled(true);
		evs.setUrl("http://127.0.0.1:1");
		evs.getOauth2().setTokenUri("http://127.0.0.1:1/token");

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> new ExternalVariableServiceClient(properties));

		assertTrue(exception.getMessage().contains("client-id, client-secret"),
				exception.getMessage());
		evs.setEnabled(false);
		assertDoesNotThrow(() -> new ExternalVariableServiceClient(properties));
	}
}
