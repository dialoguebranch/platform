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

import com.dialoguebranch.web.service.DlbProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal External Variable Service for tests: answers every request with a fixed status and
 * body after a delay, and records what it received. It also plays the authorization server, at
 * {@code /token}: it issues {@code token-1}, {@code token-2}, ... with the client-credentials
 * grant, valid for {@link #tokenLifetimeSeconds} seconds, or answers with {@link #tokenStatus}.
 */
class FakeExternalVariableService {

	record Request(String path, String query, String authorization, String body) { }

	private final HttpServer server;
	private final List<Request> requests = new CopyOnWriteArrayList<>();
	private final List<Request> tokenRequests = new CopyOnWriteArrayList<>();
	private final AtomicInteger tokensIssued = new AtomicInteger();
	volatile int tokenStatus = 200;
	volatile int tokenLifetimeSeconds = 300;

	private FakeExternalVariableService(int status, String body, Duration delay)
			throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String received = new String(exchange.getRequestBody().readAllBytes(),
					StandardCharsets.UTF_8);
			requests.add(new Request(exchange.getRequestURI().getPath(),
					exchange.getRequestURI().getRawQuery(),
					exchange.getRequestHeaders().getFirst("Authorization"), received));
			try {
				Thread.sleep(delay.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(bytes);
				}
			}
			exchange.close();
		});
		server.createContext("/token", exchange -> {
			tokenRequests.add(new Request(exchange.getRequestURI().getPath(), null,
					exchange.getRequestHeaders().getFirst("Authorization"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			if (tokenStatus != 200) {
				respond(exchange, tokenStatus, "{\"error\":\"invalid_client\"}");
			} else {
				respond(exchange, 200, "{\"access_token\":\"token-" + tokensIssued.incrementAndGet()
						+ "\",\"token_type\":\"Bearer\",\"expires_in\":" + tokenLifetimeSeconds
						+ "}");
			}
		});
		server.start();
	}

	private static void respond(HttpExchange exchange, int status, String body)
			throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
		exchange.close();
	}

	static FakeExternalVariableService answering(int status, String body, Duration delay)
			throws IOException {
		return new FakeExternalVariableService(status, body, delay);
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	String tokenUri() {
		return url() + "/token";
	}

	List<Request> requests() {
		return requests;
	}

	List<Request> tokenRequests() {
		return tokenRequests;
	}

	/**
	 * Points the given settings at this service, as both the External Variable Service and the
	 * authorization server it gets its tokens from.
	 */
	void configure(DlbProperties.ExternalVariableService evs) {
		evs.setEnabled(true);
		evs.setUrl(url());
		evs.setApiVersion(1);
		evs.getOauth2().setTokenUri(tokenUri());
		evs.getOauth2().setClientId("dlb-web-service-evs");
		evs.getOauth2().setClientSecret("test-secret");
	}

	void stop() {
		server.stop(0);
	}
}
