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
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
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
import java.util.UUID;

/**
 * Controller for the {@code /project/...} end-points of the Dialogue Branch Web Service. These
 * end-points allow authorised users (with the {@code editor} or {@code admin} role) to manage
 * project entities themselves — their metadata and language mappings — as opposed to the draft
 * dialogue content within a project, which is handled by {@link AuthoringController}.
 *
 * <p>Following the convention used throughout the rest of this API, every end-point is a short,
 * fixed action name (e.g. {@code /create-project}) that takes its parameters as query parameters
 * rather than as path variables, and only {@code GET} and {@code POST} are used.</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/project", "/project"})
@Tag(name = "6. Projects", description = "End-points for managing projects and their language " +
		"mappings.")
public class ProjectController {

	@Autowired
	Application application;

	private final ProjectService projectService;
	private final DraftProjectService draftProjectService;
	private final DraftDialogueService draftDialogueService;

	private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param projectService       service used to look up, create, and delete {@link DBProject}s.
	 * @param draftProjectService  service used to read and edit a project's draft metadata
	 *                             (display name, description) and draft translation languages —
	 *                             the working copy edited in Authoring Mode, only taking effect
	 *                             once published (see {@link PublishController}).
	 * @param draftDialogueService service used to check which draft dialogues currently have
	 *                             content in a translation language, before it's removed.
	 */
	public ProjectController(ProjectService projectService, DraftProjectService draftProjectService,
			DraftDialogueService draftDialogueService) {
		this.projectService = projectService;
		this.draftProjectService = draftProjectService;
		this.draftDialogueService = draftDialogueService;
	}

	// ---------------------------------------------------------------- //
	// -------------------- Project Management -------------------- //
	// ---------------------------------------------------------------- //

	/**
	 * Lists all projects.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @return the list of all projects.
	 * @throws HttpException if the user is not authorized.
	 */
	@Operation(summary = "List all projects.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/list-projects")
	public List<DBProject> listProjects(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/project/list-projects [user: {}]", version, user);
					return projectService.listProjects();
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Gets a single project by its slug.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to retrieve.
	 * @return the matching project.
	 * @throws HttpException if the project does not exist or the user is not authorized.
	 */
	@Operation(summary = "Get a single project by slug.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/get-project")
	public DBProject getProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/project/get-project [user: {}]", version, user);
					return projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Creates a new project with the given slug, display name, description, and source
	 * language, along with a starter draft dialogue to get the project off the ground.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param payload the new project's slug, display name, description, and source language.
	 * @return the newly created project.
	 * @throws HttpException if required fields are missing, a project with the given slug
	 * already exists, or the user is not authorized.
	 */
	@Operation(summary = "Create a new project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/create-project")
	@ResponseStatus(HttpStatus.CREATED)
	public DBProject createProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestBody CreateProjectPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/create-project [user: {}]", version, user);
					if (payload.getSlug() == null || payload.getSlug().isBlank())
						throw new BadRequestException("Field 'slug' is required.");
					if (payload.getDefaultLanguageCode() == null || payload.getDefaultLanguageCode().isBlank())
						throw new BadRequestException("Field 'defaultLanguageCode' is required.");
					if (payload.getDefaultLanguageName() == null || payload.getDefaultLanguageName().isBlank())
						throw new BadRequestException("Field 'defaultLanguageName' is required.");
					if (projectService.findBySlug(payload.getSlug()).isPresent())
						throw new ConflictException(
								"A project with slug '" + payload.getSlug() + "' already exists.");
					return projectService.createProjectWithStarterDialogue(payload.getSlug(),
							payload.getDisplayName(), payload.getDescription(),
							payload.getDefaultLanguageCode(), payload.getDefaultLanguageName());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Updates a project's draft display name and description. This is the working copy edited in
	 * Authoring Mode — it does not affect the project's published display name/description until
	 * the project is next published (see {@link PublishController}).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to update.
	 * @param payload the new draft display name and description for the project.
	 * @return the updated project.
	 * @throws HttpException if the project does not exist or the user is not authorized.
	 */
	@Operation(summary = "Update a project's draft display name and description.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/update-project")
	public DBProject updateProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestBody UpdateProjectPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/update-project [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					return draftProjectService.updateDraftMetadata(project,
							payload.getDisplayName(), payload.getDescription());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Applies a whole "Save Draft" batch from the Configure Project window in one atomic request:
	 * the project's draft display name/description, translation languages to remove, translation
	 * languages to add, and existing translation languages to rename (name and/or code). The
	 * entire batch is validated up front; if any part of it is invalid, none of it is applied (see
	 * {@link DraftProjectService#updateDraft}).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to update.
	 * @param payload the batch of draft changes to apply.
	 * @return the updated project.
	 * @throws HttpException if the project does not exist, the batch is invalid (a translation
	 * language id doesn't resolve to one owned by this project, a name/code is missing, or two
	 * languages would end up sharing a code), or the user is not authorized.
	 */
	@Operation(summary = "Apply a batch of draft changes (metadata and/or translation languages) "
			+ "to a project in one atomic request.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/update-draft")
	public DBProject updateDraft(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestBody UpdateProjectDraftPayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/update-draft [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					return draftProjectService.updateDraft(project, payload.getDisplayName(),
							payload.getDescription(), payload.getRemoveLanguageIds(),
							payload.getAddLanguages(), payload.getUpdateLanguages());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Deletes a project and all its data (draft and published dialogues, translations, and
	 * language mappings). This is a permanent, unrecoverable delete of the project entity
	 * itself — unlike dialogue deletion within a project, there is no restore path.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to delete.
	 * @throws HttpException if the project does not exist or the user is not authorized.
	 */
	@Operation(summary = "Delete a project and all its data.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/delete-project")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteProject(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/delete-project [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					projectService.deleteProject(project);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	// ------------------------------------------------------------------------ //
	// -------------------- Translation Language Management -------------------- //
	// ------------------------------------------------------------------------ //

	/**
	 * Resolves the draft translation language with the given {@code id}, checking that it belongs
	 * to {@code project}.
	 *
	 * @param project               the project the translation language must belong to.
	 * @param translationLanguageId the id of the draft translation language to resolve.
	 * @return the matching draft translation language.
	 * @throws NotFoundException if no such translation language exists, or it belongs to a
	 * different project.
	 */
	private DBDraftTranslationLanguage resolveOwnedDraftLanguage(DBProject project,
			UUID translationLanguageId) throws NotFoundException {
		return draftProjectService.findById(translationLanguageId)
				.filter(l -> l.getProject().getId().equals(project.getId()))
				.orElseThrow(() -> new NotFoundException(
						"Translation language not found: " + translationLanguageId));
	}

	/**
	 * Adds an additional draft translation language to a project, on top of its fixed source
	 * language. This does not affect the project's published translation languages until the
	 * project is next published (see {@link PublishController}).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to add the language to.
	 * @param payload the display name and language code of the translation language to add.
	 * @return the newly added draft translation language.
	 * @throws HttpException if the project does not exist, a (non-deleted) translation language
	 * with the same code already exists, or the user is not authorized.
	 */
	@Operation(summary = "Add a draft translation language to a project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/add-translation-language")
	@ResponseStatus(HttpStatus.CREATED)
	public DBDraftTranslationLanguage addTranslationLanguage(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestBody AddTranslationLanguagePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/add-translation-language [user: {}]", version,
							user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					return draftProjectService.addDraftLanguage(project,
							payload.getTranslationLanguageName(), payload.getTranslationLanguageCode());
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Removes a draft translation language from a project. A soft delete — reversible until the
	 * project is next published, at which point the language (and any draft content still in it)
	 * is actually removed. Use {@code /find-language-references} first to warn about draft
	 * dialogues that currently have content in this language.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to remove the language from.
	 * @param translationLanguageId the id of the draft translation language to remove.
	 * @throws HttpException if the project or translation language does not exist, or the user is
	 * not authorized.
	 */
	@Operation(summary = "Remove a draft translation language from a project.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/remove-translation-language")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeTranslationLanguage(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "translationLanguageId") UUID translationLanguageId
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/remove-translation-language [user: {}]",
							version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftTranslationLanguage language =
							resolveOwnedDraftLanguage(project, translationLanguageId);
					draftProjectService.removeDraftLanguage(language);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Reverts a pending deletion previously made via {@code /remove-translation-language}, marking
	 * the language as no longer pending deletion. Has no effect once the project has been
	 * published since the removal (at which point the draft row is gone for good).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to restore the language for.
	 * @param translationLanguageId the id of the draft translation language to restore.
	 * @throws HttpException if the project or translation language does not exist, or the user is
	 * not authorized.
	 */
	@Operation(summary = "Restore a draft translation language pending deletion.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/restore-translation-language")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restoreTranslationLanguage(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "translationLanguageId") UUID translationLanguageId
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/project/restore-translation-language [user: {}]",
							version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftTranslationLanguage language =
							resolveOwnedDraftLanguage(project, translationLanguageId);
					draftProjectService.restoreDraftLanguage(language);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Returns the names of every draft dialogue that currently has content in the given draft
	 * translation language — informational only, used to warn an author what removing the
	 * language would affect before they confirm it.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the language belongs to.
	 * @param translationLanguageId the id of the draft translation language to check.
	 * @return the sorted, distinct list of dialogue names with content in this language.
	 * @throws HttpException if the project or translation language does not exist, or the user is
	 * not authorized.
	 */
	@Operation(summary = "Find draft dialogues with content in a given draft translation language.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/find-language-references")
	public List<String> findLanguageReferences(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "translationLanguageId") UUID translationLanguageId
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/project/find-language-references [user: {}]", version,
							user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftTranslationLanguage language =
							resolveOwnedDraftLanguage(project, translationLanguageId);
					return draftDialogueService.findDialoguesUsingLanguage(language);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_ADMIN);
	}

}
