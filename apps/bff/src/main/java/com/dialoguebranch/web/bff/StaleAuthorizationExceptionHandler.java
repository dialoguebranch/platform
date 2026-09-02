/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.web.bff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A session's own cookie can outlive Keycloak's refresh token (its lifetime is a separate
 * Keycloak-side setting, not {@code dlb.bff.session-timeout}), so a still-valid session's
 * {@link org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager} lookup can
 * fail even though the caller looks authenticated. Left uncaught, that either surfaces as a bare
 * 500 or falls into Spring's default reauthorization redirect, which an XHR call follows
 * cross-origin into Keycloak and gets blocked by CORS, wedging Studio with nothing to
 * recover from. Catching it here instead, for every controller, clears the stale session and
 * returns a plain 401, the same signal an unauthenticated request already gets (see
 * {@code SecurityConfig.apiAuthenticationEntryPoint}), so the client's existing 401 handler takes
 * over with a normal login redirect.
 *
 * @author Dennis Hofs
 */
@RestControllerAdvice
public class StaleAuthorizationExceptionHandler {

	/**
	 * @param request the failing request, used to invalidate its session.
	 * @return an empty 401 response.
	 */
	@ExceptionHandler(OAuth2AuthorizationException.class)
	public ResponseEntity<Void> handleStaleAuthorization(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}
}
