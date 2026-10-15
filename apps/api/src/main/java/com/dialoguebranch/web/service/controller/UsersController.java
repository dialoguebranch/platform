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

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.controller.schema.ErasedUserSummary;
import com.dialoguebranch.web.service.controller.schema.UserSummary;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.storage.ExternalVariableServiceUpdater;
import com.dialoguebranch.web.service.storage.UserDataEraseService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controller for the {@code /users} end-points: a realm-scoped listing of the Dialogue Branch users
 * this service knows (i.e. has executed a dialogue for), so that a client can resolve a username
 * to the {@code subject} that delegated dialogue execution requires (see issue #130), and erasing
 * everything this service stores about one of them (#320).
 *
 * <p>Purely local {@code users}-table operations — no Keycloak call. Both are scoped to the
 * caller's own token issuer, matching delegation being realm-scoped.</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/users", "/users"})
@Tag(name = "8. Users", description = "Listing of known Dialogue Branch users, for resolving a " +
		"username to a subject, and erasing a user's data.")
public class UsersController {

	/** Hard cap on the page size, whatever the client asks for. */
	private static final int MAX_PAGE_SIZE = 200;

	private static final Logger logger = LoggerFactory.getLogger(UsersController.class);

	private final Application application;
	private final DBUserRepository userRepository;
	private final UserDataEraseService userDataEraseService;

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param application the application, for its in-memory user sessions and configuration.
	 * @param userRepository repository used to read the {@code users} table.
	 * @param userDataEraseService service that erases a user's data.
	 */
	public UsersController(Application application, DBUserRepository userRepository,
			UserDataEraseService userDataEraseService) {
		this.application = application;
		this.userRepository = userRepository;
		this.userDataEraseService = userDataEraseService;
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

	/**
	 * Erases everything this service stores about one user of the caller's realm (#320): their
	 * variables, variable history and dialogue logs in every project, and the user itself. Versions
	 * the user published stay, without a publisher. If an External Variable Service is configured,
	 * it is told that the user's variables were cleared, in each project they had variables in.
	 * Can't be undone.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param subject the stable OIDC {@code sub} of the user to erase.
	 * @return how much was erased.
	 * @throws HttpException if there is no such user, or the caller is not authorised.
	 */
	@Operation(summary = "Erase everything stored about a user (this realm only). Can't be undone.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/erase-user")
	public ErasedUserSummary eraseUser(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "subject") String subject
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/users/erase-user?subject={} [user: {}]",
							version, subject, user.subject());
					application.getApplicationManager().removeUserService(
							DialogueBranchUserId.withoutUsername(user.issuer(), subject));
					UserDataEraseService.ErasedUser erased =
							userDataEraseService.erase(user.issuer(), subject)
									.orElseThrow(() -> new NotFoundException(
											"User not found: " + subject));
					notifyExternalVariableService(user.issuer(), subject,
							erased.clearedProjects());
					return erased.summary();
				},
				version, response, "", Permission.USER_ERASE);
	}

	/**
	 * Tells the External Variable Service, if one is configured, that an erased user's variables
	 * were cleared in each of the given projects. A failure is logged but doesn't undo the erase.
	 */
	private void notifyExternalVariableService(String issuer, String subject,
			Map<String, String> clearedProjects) {
		DlbProperties properties = application.getDlbProperties();
		if (!properties.getExternalVariableService().isEnabled()) return;
		clearedProjects.forEach((projectSlug, timeZone) -> {
			try {
				ExternalVariableServiceUpdater.notifyCleared(properties, issuer, subject,
						projectSlug, timeZone);
			} catch (Exception ex) {
				logger.error("Failed to notify the External Variable Service that the variables " +
						"of erased user '{}' in project '{}' were cleared: {}",
						subject, projectSlug, ex.getMessage(), ex);
			}
		});
	}
}
