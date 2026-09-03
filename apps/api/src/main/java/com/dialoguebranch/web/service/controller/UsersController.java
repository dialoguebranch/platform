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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.controller.schema.UserSummary;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for the {@code /users} end-point: a read-only, realm-scoped listing of the Dialogue
 * Branch users this service knows (i.e. has executed a dialogue for), so that a client can resolve
 * a username to the {@code subject} that delegated dialogue execution requires (see issue #130).
 *
 * <p>Purely a local {@code users}-table read — no Keycloak call. The listing is scoped to the
 * caller's own token issuer, matching delegation being realm-scoped.</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/users", "/users"})
@Tag(name = "8. Users", description = "Read-only listing of known Dialogue Branch users, for " +
		"resolving a username to a subject.")
public class UsersController {

	/** Hard cap on the page size, whatever the client asks for. */
	private static final int MAX_PAGE_SIZE = 200;

	private static final Logger logger = LoggerFactory.getLogger(UsersController.class);

	private final DBUserRepository userRepository;

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param userRepository repository used to read the {@code users} table.
	 */
	public UsersController(DBUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Lists the users known to this service within the caller's own realm, optionally filtered by
	 * a username fragment, ordered by username and paginated.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param username an optional case-insensitive substring the username must contain.
	 * @param page the zero-based page index (default {@code 0}).
	 * @param pageSize the page size (default {@code 50}, capped at {@value #MAX_PAGE_SIZE}).
	 * @return the matching page of {@link UserSummary} entries.
	 * @throws HttpException if the caller is not authorised.
	 */
	@Operation(summary = "List the Dialogue Branch users known to this service (this realm only).")
	@Parameter(name = "version", hidden = true)
	@GetMapping
	public List<UserSummary> listUsers(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "username", required = false, defaultValue = "") String username,
			@RequestParam(value = "page", required = false, defaultValue = "0") int page,
			@RequestParam(value = "pageSize", required = false, defaultValue = "50") int pageSize
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/users?username={}&page={}&pageSize={} [user: {}]",
							version, username, page, pageSize, user.subject());
					int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
					return userRepository
							.findByIssuerAndUsernameContainingIgnoreCaseOrderByUsernameAsc(
									user.issuer(), username, PageRequest.of(Math.max(0, page), size))
							.map(u -> new UserSummary(u.getUsername(), u.getSubject()))
							.getContent();
				},
				version, response, "", Permission.USER_LIST);
	}
}
