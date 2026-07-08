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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.PublishService;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Controller for the {@code /publish/...} end-points of the Dialogue Branch Web Service. These
 * end-points validate and publish a project's current draft as a new, immutable project version
 * that becomes available to the execution engine.
 *
 * <p>Kept separate from {@link AuthoringController} (draft content editing) and
 * {@link ProjectController} (project entity management) because publishing is expected to
 * eventually require a distinct permission from authoring — someone who can edit draft dialogues
 * should not necessarily be able to publish them.</p>
 *
 * <p>Following the convention used throughout the rest of this API, every end-point is a short,
 * fixed action name that takes its parameters as query parameters rather than as path variables,
 * and only {@code GET} and {@code POST} are used.</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/publish", "/publish"})
@Tag(name = "8. Publishing", description = "End-points for publishing validated project versions.")
public class PublishController {

	@Autowired
	Application application;

	private final ProjectService projectService;
	private final PublishService publishService;

	private static final Logger logger = LoggerFactory.getLogger(PublishController.class);

	public PublishController(ProjectService projectService, PublishService publishService) {
		this.projectService = projectService;
		this.publishService = publishService;
	}

	@Operation(summary = "List all published versions of a project.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/list-versions")
	public List<DBProjectVersion> listVersions(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectName") String projectName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/publish/list-versions [user: {}]", version, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					return publishService.listVersions(project);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Validate and publish the current draft as a new project version.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/create-version")
	public PublishService.PublishResult publish(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectName") String projectName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/publish/create-version [user: {}]", version, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					try {
						return publishService.publish(project, null);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

}
