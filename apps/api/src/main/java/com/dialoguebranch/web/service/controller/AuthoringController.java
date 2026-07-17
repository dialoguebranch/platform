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
import com.dialoguebranch.web.service.project.DraftProjectService;
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
	private final DraftProjectService draftProjectService;

	private static final Logger logger = LoggerFactory.getLogger(AuthoringController.class);

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param projectService service used to look up the {@link DBProject} that a dialogue,
	 *                       node, or translation belongs to.
	 * @param draftDialogueService service that performs the actual CRUD operations on draft
	 *                             dialogues, nodes, and translations.
	 * @param draftProjectService service used to resolve a language code into the project's
	 *                            registered draft translation language a translation targets.
	 */
	public AuthoringController(ProjectService projectService,
							   DraftDialogueService draftDialogueService,
							   DraftProjectService draftProjectService) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
		this.draftProjectService = draftProjectService;
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

	/**
	 * Lists all draft dialogues in a project, including those pending creation, change, or
	 * deletion (see {@link DraftDialogueSummary}).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project whose draft dialogues should be listed.
	 * @return the list of draft dialogues in the project, summarized.
	 * @throws HttpException if the project does not exist or the user is not authorized.
	 */
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

	/**
	 * Creates a new, empty draft dialogue in a project.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to create the dialogue in.
	 * @param payload the name for the new draft dialogue.
	 * @return the newly created draft dialogue.
	 * @throws HttpException if the payload is invalid, the project does not exist, or the user
	 * is not authorized.
	 */
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

	/**
	 * Marks a draft dialogue as pending deletion. This is a soft delete: the dialogue is
	 * excluded from published output but can still be reverted via {@code /restore-dialogue}
	 * until the project is next published, at which point the deletion becomes permanent.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to mark as deleted.
	 * @throws HttpException if the project or dialogue does not exist, or the user is not
	 * authorized.
	 */
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

	/**
	 * Reverts a pending deletion made via {@code /delete-dialogue}, restoring the draft dialogue
	 * to normal editable status.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to restore.
	 * @return the restored draft dialogue.
	 * @throws HttpException if the project or dialogue does not exist, or the user is not
	 * authorized.
	 */
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

	/**
	 * Finds all {@code [[reply link]]} references elsewhere in the project that point at the
	 * given dialogue. Used by the client to warn the user before a rename or delete that would
	 * break existing cross-references.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the dialogue to find references to.
	 * @return the list of node references pointing at the dialogue.
	 * @throws HttpException if the project or dialogue does not exist, or the user is not
	 * authorized.
	 */
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

	/**
	 * Renames a draft dialogue, optionally rewriting {@code [[reply link]]} references to it
	 * elsewhere in the project so they keep pointing at the renamed dialogue.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the current name of the dialogue to rename.
	 * @param newName the new name for the dialogue.
	 * @param updateReferences whether to rewrite references to this dialogue elsewhere in the
	 *                         project.
	 * @return the rename result, including the renamed dialogue and the number of references
	 * updated.
	 * @throws HttpException if {@code newName} is blank, the dialogue is pending deletion, the
	 * project or dialogue does not exist, or the user is not authorized.
	 */
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

	/**
	 * Lists all nodes in a draft dialogue.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue whose nodes should be listed.
	 * @return the list of nodes in the dialogue.
	 * @throws HttpException if the project or dialogue does not exist, or the user is not
	 * authorized.
	 */
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

	/**
	 * Creates a new node in a draft dialogue.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to add the node to.
	 * @param payload the title, header, and body of the new node.
	 * @return the newly created node.
	 * @throws HttpException if the payload is invalid, the dialogue is pending deletion, the
	 * project or dialogue does not exist, or the user is not authorized.
	 */
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

	/**
	 * Updates the header and body of a draft node.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue the node belongs to.
	 * @param nodeTitle the title of the node to update.
	 * @param payload the new header and body content for the node.
	 * @return the updated node.
	 * @throws HttpException if the dialogue is pending deletion, the project, dialogue, or node
	 * does not exist, or the user is not authorized.
	 */
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
					return draftDialogueService.updateNode(dialogue, node, payload.getHeader(),
							payload.getBody());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Deletes a node from a draft dialogue. Unlike dialogue deletion, this is a hard delete with
	 * no restore path.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue the node belongs to.
	 * @param nodeTitle the title of the node to delete.
	 * @throws HttpException if the dialogue is pending deletion, the project, dialogue, or node
	 * does not exist, or the user is not authorized.
	 */
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
					draftDialogueService.deleteNode(dialogue, node);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Finds all {@code [[reply link]]} references elsewhere in the project that point at the
	 * given node. Used by the client to warn the user before a rename or delete that would break
	 * existing cross-references.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue the node belongs to.
	 * @param nodeTitle the title of the node to find references to.
	 * @return the list of node references pointing at the node.
	 * @throws HttpException if the project, dialogue, or node does not exist, or the user is not
	 * authorized.
	 */
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

	/**
	 * Renames a draft node, optionally rewriting {@code [[reply link]]} references to it
	 * elsewhere in the project so they keep pointing at the renamed node.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue the node belongs to.
	 * @param oldTitle the current title of the node to rename.
	 * @param newTitle the new title for the node.
	 * @param updateReferences whether to rewrite references to this node elsewhere in the
	 *                         project.
	 * @return the rename result, including the renamed node and the number of references
	 * updated.
	 * @throws HttpException if {@code newTitle} is blank, the dialogue is pending deletion, the
	 * project, dialogue, or node does not exist, or the user is not authorized.
	 */
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

	/**
	 * Creates or updates the translation for a draft dialogue in a given language.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to translate.
	 * @param language the language code of the translation to create or update.
	 * @param payload the raw JSON translation content.
	 * @return the created or updated translation.
	 * @throws HttpException if the dialogue is pending deletion, the project or dialogue does
	 * not exist, or the user is not authorized.
	 */
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
					DBDraftTranslationLanguage translationLanguage = draftProjectService
							.findDraftLanguage(project, language)
							.orElseThrow(() -> new NotFoundException(
									"Translation language not registered for this project: " +
											language));
					return draftDialogueService.createOrUpdateTranslation(dialogue,
							translationLanguage, payload.getContent());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Deletes the translation for a draft dialogue in a given language.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue the translation belongs to.
	 * @param language the language code of the translation to delete.
	 * @throws HttpException if the dialogue is pending deletion, the project, dialogue, or
	 * translation does not exist, or the user is not authorized.
	 */
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
					DBDraftTranslationLanguage translationLanguage = draftProjectService
							.findDraftLanguage(project, language)
							.orElseThrow(() -> new NotFoundException(
									"Translation language not registered for this project: " +
											language));
					DBDraftTranslation translation = draftDialogueService
							.findTranslation(dialogue, translationLanguage)
							.orElseThrow(() -> new NotFoundException(
									"Translation not found for language: " + language));
					draftDialogueService.deleteTranslation(dialogue, translation);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Returns the translation for a draft dialogue in a given language, or {@code null} if none
	 * exists yet (not an error — a project language may simply have no translations started).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue whose translation should be returned.
	 * @param language the language code of the translation to return.
	 * @return the matching translation, or {@code null} if none exists.
	 * @throws HttpException if the project or dialogue does not exist, or the user is not
	 * authorized.
	 */
	@Operation(summary = "Get the translation for a draft dialogue in a given language.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/get-translation")
	public DBDraftTranslation getTranslation(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "language") String language
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/get-translation [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					DBDraftTranslationLanguage translationLanguage = draftProjectService
							.findDraftLanguage(project, language)
							.orElseThrow(() -> new NotFoundException(
									"Translation language not registered for this project: " +
											language));
					return draftDialogueService.findTranslation(dialogue, translationLanguage)
							.orElse(null);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Extracts every translatable term from a draft dialogue's current content.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to extract translatable terms from.
	 * @return the dialogue's translatable terms.
	 * @throws HttpException if the dialogue's current draft content does not currently parse, the
	 * project or dialogue does not exist, or the user is not authorized.
	 */
	@Operation(summary = "List all translatable terms in a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/list-translatable-terms")
	public List<TranslatableTermSummary> listTranslatableTerms(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/list-translatable-terms [user: {}]", version,
							user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					return draftDialogueService.listTranslatableTerms(dialogue);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

}
