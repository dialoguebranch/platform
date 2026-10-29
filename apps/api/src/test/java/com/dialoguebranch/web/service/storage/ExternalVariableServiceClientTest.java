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

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalVariableServiceClientTest {

	private FakeExternalVariableService service;

	@AfterEach
	void stopService() {
		if (service != null) service.stop();
	}

	private static ExternalVariableServiceClient clientFor(String url, Duration readTimeout) {
		DlbProperties properties = new DlbProperties();
		DlbProperties.ExternalVariableService evs = properties.getExternalVariableService();
		evs.setEnabled(true);
		evs.setUrl(url);
		evs.setApiVersion(1);
		evs.setApiKey("test-key");
		evs.setConnectTimeout(Duration.ofMillis(500));
		evs.setReadTimeout(readTimeout);
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
		ExternalVariableServiceClient client = clientFor(unreachableUrl(), Duration.ofSeconds(1));

		ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class,
				() -> client.getSupportedVariables("default-test"));

		assertEquals(ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
				exception.getError().getCode());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
				ServiceUnavailableException.class.getAnnotation(ResponseStatus.class).value());
	}

	@Test
	void anUnreachableServiceNeverBreaksTheCaller() throws IOException {
		ExternalVariableServiceClient client = clientFor(unreachableUrl(), Duration.ofSeconds(1));

		assertEquals(0, client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false)
				.length);
		assertDoesNotThrow(() -> client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable()));
		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
	}

	@Test
	void aServiceThatAnswersWithAnErrorNeverBreaksTheCaller() throws IOException {
		service = FakeExternalVariableService.answering(500, "", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service.url(), Duration.ofSeconds(1));

		assertEquals(0, client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), false)
				.length);
		assertDoesNotThrow(() -> client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable()));
		assertDoesNotThrow(() -> client.notifyCleared("iss", "sub", "p", "UTC"));
		assertThrows(ServiceUnavailableException.class, () -> client.getSupportedVariables("p"));
	}

	@Test
	void aSlowServiceIsCutOffByTheReadTimeout() throws IOException {
		service = FakeExternalVariableService.answering(200, "[]", Duration.ofSeconds(3));
		ExternalVariableServiceClient client = clientFor(service.url(), Duration.ofMillis(300));

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
		ExternalVariableServiceClient client = clientFor(service.url(), Duration.ofSeconds(1));

		Variable[] updates = client.retrieveUpdates("iss", "sub", "p", "UTC", oneVariable(), true);

		assertEquals(1, updates.length);
		assertEquals("steps", updates[0].getName());
		FakeExternalVariableService.Request request = service.requests().get(0);
		assertEquals("/v1/variables/retrieve-updates", request.path());
		assertTrue(request.query().contains("subject=sub"), request.query());
		assertTrue(request.query().contains("testSession=true"), request.query());
		assertEquals("Bearer test-key", request.authorization());
	}

	@Test
	void notifyUpdatedSendsTheChangedVariables() throws IOException {
		service = FakeExternalVariableService.answering(200, "", Duration.ZERO);
		ExternalVariableServiceClient client = clientFor(service.url(), Duration.ofSeconds(1));

		client.notifyUpdated("iss", "sub", "p", "UTC", oneVariable());

		FakeExternalVariableService.Request request = service.requests().get(0);
		assertEquals("/v1/variables/notify-updated", request.path());
		assertTrue(request.body().contains("\"steps\""), request.body());
	}
}
