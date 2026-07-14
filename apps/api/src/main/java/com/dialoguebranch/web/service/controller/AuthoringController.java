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
import com.dialoguebranch.web.service.controller.schema.authoring.*;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
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

import java.util.List;

/**
 * Controller for the {@code /authoring/...} end-points of the Dialogue Branch Web Service. These
 * end-points allow authorised users (with the {@code editor} or {@code admin} role) to manage the
 * draft dialogue content within a project — dialogues, their nodes, and their translations.
 *
 * <p>Project entity management (metadata, language mappings) lives in
 * {@link ProjectController}; publishing a draft as a new version lives in
 * {@link PublishController}.</p>
 *
 * <p>Following the convention used throughout the rest of this API, every end-point is a short,
 * fixed action name (e.g. {@code /create-node}) that takes its parameters as query parameters
 * rather than as path variables, and only {@code GET} and {@code POST} are used.</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/authoring", "/authoring"})
@Tag(name = "7. Authoring", description = "End-points for managing draft dialogues, their " +
		"nodes, and their translations.")
public class AuthoringController {

	@Autowired
	Application application;

	private final ProjectService projectService;
	private final DraftDialogueService draftDialogueService;

	private static final Logger logger = LoggerFactory.getLogger(AuthoringController.class);

	public AuthoringController(ProjectService projectService,
							   DraftDialogueService draftDialogueService) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
	}

	/**
	 * Rejects further editing (or test-running, see {@link DraftExecutionController}) of a
	 * dialogue that is currently pending deletion, until that deletion is reverted via
	 * {@code /restore-dialogue}.
	 *
	 * @param dialogue the dialogue to check.
	 * @throws ConflictException if {@code dialogue} is pending deletion.
	 */
	static void checkNotDeleted(DBDraftDialogue dialogue) throws ConflictException {
		if (dialogue.getIsDeleted()) {
			throw new ConflictException("Dialogue '" + dialogue.getName() + "' is pending " +
					"deletion — restore it first.");
		}
	}

	// ------------------------------------------------------------------ //
	// -------------------- Dialogue Management -------------------- //
	// ------------------------------------------------------------------ //

	@Operation(summary = "List all draft dialogues in a project.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/list-dialogues")
	public List<DraftDialogueSummary> listDialogues(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/list-dialogues [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					return draftDialogueService.listDialogues(project).stream()
							.map(DraftDialogueSummary::new)
							.toList();
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Create a new draft dialogue in a project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/create-dialogue")
	@ResponseStatus(HttpStatus.CREATED)
	public DBDraftDialogue createDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestBody CreateDialoguePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/create-dialogue [user: {}]", version, user);
					if (payload.getName() == null || payload.getName().isBlank())
						throw new BadRequestException("Field 'name' is required.");
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					return draftDialogueService.createDialogue(project, payload.getName());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Mark a draft dialogue as pending deletion (revertible via " +
			"/restore-dialogue until the project is next published).")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/delete-dialogue")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/delete-dialogue [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					draftDialogueService.deleteDialogue(dialogue);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Revert a pending deletion made via /delete-dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/restore-dialogue")
	public DBDraftDialogue restoreDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/restore-dialogue [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					draftDialogueService.restoreDialogue(dialogue);
					return dialogue;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Find all reply links in the project that reference a given dialogue.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/find-dialogue-references")
	public List<DraftDialogueService.NodeReference> findDialogueReferences(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/find-dialogue-references [user: {}]", version,
							user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					draftDialogueService.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					return draftDialogueService.findDialogueReferences(project, dialogueName);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Rename a draft dialogue, optionally rewriting references to it " +
			"elsewhere in the project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/rename-dialogue")
	public DraftDialogueService.DialogueRenameResult renameDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "newName") String newName,
			@RequestParam(value = "updateReferences", defaultValue = "false")
			boolean updateReferences
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/rename-dialogue [user: {}]", version, user);
					if (newName == null || newName.isBlank())
						throw new BadRequestException("Field 'newName' is required.");
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					return draftDialogueService.renameDialogue(project, dialogue, newName,
							updateReferences);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	// ------------------------------------------------------------- //
	// -------------------- Node Management -------------------- //
	// ------------------------------------------------------------- //

	@Operation(summary = "List all nodes in a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/list-nodes")
	public List<DBDraftNode> listNodes(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/list-nodes [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					return draftDialogueService.listNodes(dialogue);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Create a new node in a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/create-node")
	@ResponseStatus(HttpStatus.CREATED)
	public DBDraftNode createNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestBody CreateNodePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/create-node [user: {}]", version, user);
					if (payload.getTitle() == null || payload.getTitle().isBlank())
						throw new BadRequestException("Field 'title' is required.");
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					return draftDialogueService.createNode(dialogue, payload.getTitle(),
							payload.getHeader(), payload.getBody());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Update the header and body of a draft node.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/update-node")
	public DBDraftNode updateNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "nodeTitle") String nodeTitle,
			@RequestBody UpdateNodePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/update-node [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					DBDraftNode node = draftDialogueService.findNode(dialogue, nodeTitle)
							.orElseThrow(() -> new NotFoundException(
									"Node not found: " + nodeTitle));
					return draftDialogueService.updateNode(node, payload.getHeader(),
							payload.getBody());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Delete a node from a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/delete-node")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "nodeTitle") String nodeTitle
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/delete-node [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					DBDraftNode node = draftDialogueService.findNode(dialogue, nodeTitle)
							.orElseThrow(() -> new NotFoundException(
									"Node not found: " + nodeTitle));
					draftDialogueService.deleteNode(node);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Find all reply links in the project that reference a given node.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/find-node-references")
	public List<DraftDialogueService.NodeReference> findNodeReferences(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "nodeTitle") String nodeTitle
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/find-node-references [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					draftDialogueService.findNode(dialogue, nodeTitle)
							.orElseThrow(() -> new NotFoundException(
									"Node not found: " + nodeTitle));
					return draftDialogueService.findNodeReferences(project, dialogueName, nodeTitle);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Rename a draft node, optionally rewriting references to it elsewhere " +
			"in the project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/rename-node")
	public DraftDialogueService.RenameResult renameNode(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "oldTitle") String oldTitle,
			@RequestParam(value = "newTitle") String newTitle,
			@RequestParam(value = "updateReferences", defaultValue = "false")
			boolean updateReferences
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/rename-node [user: {}]", version, user);
					if (newTitle == null || newTitle.isBlank())
						throw new BadRequestException("Field 'newTitle' is required.");
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					DBDraftNode node = draftDialogueService.findNode(dialogue, oldTitle)
							.orElseThrow(() -> new NotFoundException(
									"Node not found: " + oldTitle));
					return draftDialogueService.renameNode(project, dialogue, node, newTitle,
							updateReferences);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	// ----------------------------------------------------------------------- //
	// -------------------- Translation Management -------------------- //
	// ----------------------------------------------------------------------- //

	@Operation(summary = "Create or update a translation for a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/update-translation")
	public DBDraftTranslation updateTranslation(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "language") String language,
			@RequestBody UpdateTranslationPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/update-translation [user: {}]", version,
							user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					return draftDialogueService.createOrUpdateTranslation(dialogue, language,
							payload.getContent());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	@Operation(summary = "Delete a translation from a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/delete-translation")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTranslation(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "language") String language
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/delete-translation [user: {}]", version,
							user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					DBDraftTranslation translation = draftDialogueService
							.findTranslation(dialogue, language)
							.orElseThrow(() -> new NotFoundException(
									"Translation not found for language: " + language));
					draftDialogueService.deleteTranslation(translation);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

}
