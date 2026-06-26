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

import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.controller.schema.authoring.*;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.PublishService;
import com.dialoguebranch.web.service.storage.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Controller for the {@code /authoring/...} end-points of the Dialogue Branch Web Service.
 * These end-points allow authorised users (with the {@code editor} or {@code admin} role) to
 * manage projects and draft dialogues, and to publish validated project versions that become
 * available to the execution engine.
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = {"/v{version}/authoring", "/authoring"})
@Tag(name = "6. Authoring", description = "End-points for managing projects, draft dialogues, " +
		"and publishing validated project versions.")
public class AuthoringController {

	@Autowired
	Application application;

	private final ProjectService projectService;
	private final DraftDialogueService draftDialogueService;
	private final PublishService publishService;

	private static final Logger logger = LoggerFactory.getLogger(AuthoringController.class);

	public AuthoringController(ProjectService projectService,
							   DraftDialogueService draftDialogueService,
							   PublishService publishService) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
		this.publishService = publishService;
	}

	// ---------------------------------------------------------------- //
	// -------------------- Project Management -------------------- //
	// ---------------------------------------------------------------- //

	@Operation(summary = "List all projects.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/project")
	public List<DBProject> listProjects(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/project [user: {}]", version, user);
					return projectService.listProjects();
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Get a single project by name.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/project/{projectName}")
	public DBProject getProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/project/{} [user: {}]", version, projectName,
							user);
					return projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Create a new project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/project")
	@ResponseStatus(HttpStatus.CREATED)
	public DBProject createProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@RequestBody CreateProjectPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/project [user: {}]", version, user);
					if (payload.getName() == null || payload.getName().isBlank())
						throw new BadRequestException("Field 'name' is required.");
					return projectService.createProject(payload.getName(),
							payload.getDisplayName(), payload.getDescription());
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Update a project's display name and description.")
	@Parameter(name = "version", hidden = true)
	@PutMapping("/project/{projectName}")
	public DBProject updateProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@RequestBody UpdateProjectPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("PUT /v{}/authoring/project/{} [user: {}]", version, projectName,
							user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					return projectService.updateProject(project, payload.getDisplayName(),
							payload.getDescription());
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Delete a project and all its data.")
	@Parameter(name = "version", hidden = true)
	@DeleteMapping("/project/{projectName}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("DELETE /v{}/authoring/project/{} [user: {}]", version,
							projectName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					projectService.deleteProject(project);
					return null;
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_ADMIN);
	}

	// ------------------------------------------------------------------------ //
	// -------------------- Language Mapping Management -------------------- //
	// ------------------------------------------------------------------------ //

	@Operation(summary = "Add a language mapping to a project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/project/{projectName}/language-mapping")
	@ResponseStatus(HttpStatus.CREATED)
	public DBProjectLanguageMapping addLanguageMapping(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@RequestBody AddLanguageMappingPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/project/{}/language-mapping [user: {}]",
							version, projectName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					Language source = new Language(payload.getSourceLanguageName(),
							payload.getSourceLanguageCode());
					Language translation = new Language(payload.getTranslationLanguageName(),
							payload.getTranslationLanguageCode());
					return projectService.addLanguageMapping(project, source, translation);
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Remove a language mapping from a project.")
	@Parameter(name = "version", hidden = true)
	@DeleteMapping("/project/{projectName}/language-mapping/{mappingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeLanguageMapping(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable UUID mappingId
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info(
							"DELETE /v{}/authoring/project/{}/language-mapping/{} [user: {}]",
							version, projectName, mappingId, user);
					projectService.removeLanguageMapping(mappingId);
					return null;
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_ADMIN);
	}

	// ------------------------------------------------------------------ //
	// -------------------- Dialogue Management -------------------- //
	// ------------------------------------------------------------------ //

	@Operation(summary = "List all draft dialogues in a project.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/project/{projectName}/dialogue")
	public List<DBDraftDialogue> listDialogues(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/project/{}/dialogue [user: {}]", version,
							projectName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					return draftDialogueService.listDialogues(project);
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Create a new draft dialogue in a project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/project/{projectName}/dialogue")
	@ResponseStatus(HttpStatus.CREATED)
	public DBDraftDialogue createDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@RequestBody CreateDialoguePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/project/{}/dialogue [user: {}]", version,
							projectName, user);
					if (payload.getName() == null || payload.getName().isBlank())
						throw new BadRequestException("Field 'name' is required.");
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					return draftDialogueService.createDialogue(project, payload.getName());
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Delete a draft dialogue and all its nodes and translations.")
	@Parameter(name = "version", hidden = true)
	@DeleteMapping("/project/{projectName}/dialogue/{dialogueName}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("DELETE /v{}/authoring/project/{}/dialogue/{} [user: {}]",
							version, projectName, dialogueName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					draftDialogueService.deleteDialogue(dialogue);
					return null;
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	// ------------------------------------------------------------- //
	// -------------------- Node Management -------------------- //
	// ------------------------------------------------------------- //

	@Operation(summary = "List all nodes in a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/project/{projectName}/dialogue/{dialogueName}/node")
	public List<DBDraftNode> listNodes(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/project/{}/dialogue/{}/node [user: {}]",
							version, projectName, dialogueName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					return draftDialogueService.listNodes(dialogue);
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Create a new node in a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/project/{projectName}/dialogue/{dialogueName}/node")
	@ResponseStatus(HttpStatus.CREATED)
	public DBDraftNode createNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName,
			@RequestBody CreateNodePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/project/{}/dialogue/{}/node [user: {}]",
							version, projectName, dialogueName, user);
					if (payload.getTitle() == null || payload.getTitle().isBlank())
						throw new BadRequestException("Field 'title' is required.");
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					return draftDialogueService.createNode(dialogue, payload.getTitle(),
							payload.getHeader(), payload.getBody());
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Update the header and body of a draft node.")
	@Parameter(name = "version", hidden = true)
	@PutMapping("/project/{projectName}/dialogue/{dialogueName}/node/{nodeTitle}")
	public DBDraftNode updateNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName,
			@PathVariable String nodeTitle,
			@RequestBody UpdateNodePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info(
							"PUT /v{}/authoring/project/{}/dialogue/{}/node/{} [user: {}]",
							version, projectName, dialogueName, nodeTitle, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					DBDraftNode node = draftDialogueService.findNode(dialogue, nodeTitle)
							.orElseThrow(() -> new NotFoundException(
									"Node not found: " + nodeTitle));
					return draftDialogueService.updateNode(node, payload.getHeader(),
							payload.getBody());
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Delete a node from a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@DeleteMapping("/project/{projectName}/dialogue/{dialogueName}/node/{nodeTitle}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName,
			@PathVariable String nodeTitle
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info(
							"DELETE /v{}/authoring/project/{}/dialogue/{}/node/{} [user: {}]",
							version, projectName, dialogueName, nodeTitle, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					DBDraftNode node = draftDialogueService.findNode(dialogue, nodeTitle)
							.orElseThrow(() -> new NotFoundException(
									"Node not found: " + nodeTitle));
					draftDialogueService.deleteNode(node);
					return null;
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	// ----------------------------------------------------------------------- //
	// -------------------- Translation Management -------------------- //
	// ----------------------------------------------------------------------- //

	@Operation(summary = "Create or update a translation for a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PutMapping("/project/{projectName}/dialogue/{dialogueName}/translation/{language}")
	public DBDraftTranslation updateTranslation(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName,
			@PathVariable String language,
			@RequestBody UpdateTranslationPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info(
							"PUT /v{}/authoring/project/{}/dialogue/{}/translation/{} [user: {}]",
							version, projectName, dialogueName, language, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					return draftDialogueService.createOrUpdateTranslation(dialogue, language,
							payload.getContent());
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Delete a translation from a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@DeleteMapping("/project/{projectName}/dialogue/{dialogueName}/translation/{language}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTranslation(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName,
			@PathVariable String dialogueName,
			@PathVariable String language
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info(
							"DELETE /v{}/authoring/project/{}/dialogue/{}/translation/{} [user: {}]",
							version, projectName, dialogueName, language, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					DBDraftTranslation translation = draftDialogueService
							.findTranslation(dialogue, language)
							.orElseThrow(() -> new NotFoundException(
									"Translation not found for language: " + language));
					draftDialogueService.deleteTranslation(translation);
					return null;
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	// ------------------------------------------------------------ //
	// -------------------- Publishing -------------------- //
	// ------------------------------------------------------------ //

	@Operation(summary = "List all published versions of a project.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/project/{projectName}/versions")
	public List<DBProjectVersion> listVersions(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/project/{}/versions [user: {}]", version,
							projectName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					return publishService.listVersions(project);
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_EDITOR, BasicUserCredentials.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Validate and publish the current draft as a new project version.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/project/{projectName}/publish")
	public PublishService.PublishResult publish(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestHeader(value = "X-Auth-Token", required = false) String token,
			@PathVariable String projectName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/project/{}/publish [user: {}]", version,
							projectName, user);
					DBProject project = projectService.findByName(projectName)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectName));
					try {
						return publishService.publish(project, null);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				},
				version, token, response, "", application,
				BasicUserCredentials.USER_ROLE_ADMIN);
	}

}
