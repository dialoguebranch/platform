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

import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.ForbiddenException;

/**
 * Decides whether an authenticated user may perform an operation, by resolving the user's roles
 * (from the Keycloak JWT) to a permission set via {@link Role#permissionsForRoles(String[])} and
 * testing the required {@link Permission} against it.
 *
 * <p>{@link #require(AuthenticationInfo, Permission)} is the enforcement entry point: it throws a
 * {@link ForbiddenException} (HTTP 403, error code {@link ErrorCode#INSUFFICIENT_PRIVILEGES}) when
 * the permission is absent. This is the "authenticated but not allowed" case; a missing or invalid
 * token is a separate concern handled earlier, as a 401. Callers must therefore only reach this
 * class with an {@link AuthenticationInfo} that already represents a validated token.</p>
 *
 * <p>{@link com.dialoguebranch.web.service.QueryRunner#runQuery} calls {@link
 * #require(AuthenticationInfo, Permission)} once per request, with the single {@link Permission}
 * that end-point declares; there are no per-controller role lists any more. Making the role &rarr;
 * permission mapping editable at runtime is a separate follow-up — see
 * <a href="https://github.com/dialoguebranch/platform/issues/99">#99</a>.</p>
 *
 * @author Harm op den Akker
 */
public final class AuthorizationService {

	/** Utility class — not instantiated. */
	private AuthorizationService() { }

	/**
	 * Returns whether {@code user} holds {@code permission} through any of its roles. A {@code null}
	 * user (no authentication) holds nothing.
	 *
	 * @param user the authenticated user, or {@code null}.
	 * @param permission the permission to test.
	 * @return {@code true} if the user is authorized for this permission.
	 */
	public static boolean hasPermission(AuthenticationInfo user, Permission permission) {
		if (user == null) {
			return false;
		}
		return Role.permissionsForRoles(user.getRoles()).contains(permission);
	}

	/**
	 * Verifies that {@code user} holds {@code permission}, throwing {@link ForbiddenException} if
	 * not.
	 *
	 * @param user the authenticated user, or {@code null}.
	 * @param permission the permission the operation requires.
	 * @throws ForbiddenException (HTTP 403, {@link ErrorCode#INSUFFICIENT_PRIVILEGES}) if the user
	 *                            does not hold the permission.
	 */
	public static void require(AuthenticationInfo user, Permission permission)
			throws ForbiddenException {
		if (!hasPermission(user, permission)) {
			String username = user != null ? user.getUsername() : "unknown";
			throw new ForbiddenException(ErrorCode.INSUFFICIENT_PRIVILEGES,
					"User '" + username + "' does not have the '" + permission + "' permission " +
					"required for this operation.");
		}
	}

}
