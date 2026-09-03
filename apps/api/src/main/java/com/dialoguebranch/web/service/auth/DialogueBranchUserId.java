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

package com.dialoguebranch.web.service.auth;

/**
 * Identifies the Dialogue Branch user a query runs as: the {@code (issuer, subject)} pair from the
 * authenticating JWT (issue #128), plus the token's {@code preferred_username} carried alongside
 * so the {@code users} row's display name can be refreshed.
 *
 * <p>Identity is the {@code (issuer, subject)} pair. {@code username} is passenger data — the
 * last-seen display name — and is {@code null} when the query runs as a delegated user (the
 * caller's token, not the delegate's, is in hand) or from the External Variable Service callback.
 * Do not use it as a lookup or equality key.</p>
 *
 * @param issuer the token issuer (the full OIDC {@code iss} URL).
 * @param subject the OIDC {@code sub} claim, stable within {@code issuer}.
 * @param username the last-seen {@code preferred_username}, or {@code null} if not in hand.
 */
public record DialogueBranchUserId(String issuer, String subject, String username) {

	/**
	 * Creates an identity with no known username (a delegated user, or the EVS callback).
	 *
	 * @param issuer the token issuer.
	 * @param subject the OIDC {@code sub} claim.
	 * @return the identity, with a {@code null} username.
	 */
	public static DialogueBranchUserId withoutUsername(String issuer, String subject) {
		return new DialogueBranchUserId(issuer, subject, null);
	}
}
