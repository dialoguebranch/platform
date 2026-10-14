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

import com.dialoguebranch.execution.parser.DialogueBranchHeader;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.controller.schema.authoring.*;
import com.dialoguebranch.web.service.controller.schema.authoring.DraftNodeResponse;
import com.dialoguebranch.web.service.controller.schema.authoring.ImportedScript;
import com.dialoguebranch.web.service.controller.schema.authoring.PreamblePayload;
import com.dialoguebranch.web.service.controller.schema.authoring.RenameNodeResponse;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.DraftProjectService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.ScriptImportService;
import com.dialoguebranch.web.service.storage.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

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

	private final ProjectService projectService;
	private final DraftDialogueService draftDialogueService;
	private final DraftProjectService draftProjectService;
	private final ScriptImportService scriptImportService;

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
	 * @param scriptImportService service that imports {@code .dlb} scripts as draft dialogues.
	 */
	public AuthoringController(ProjectService projectService,
							   DraftDialogueService draftDialogueService,
							   DraftProjectService draftProjectService,
							   ScriptImportService scriptImportService) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
		this.draftProjectService = draftProjectService;
		this.scriptImportService = scriptImportService;
	}

	/**
	 * Turns a node's header tags, as sent by a client, into header text with {@link
	 * DialogueBranchHeader#write}, refusing tags that don't form a valid header (#350).
	 *
	 * @param tags the tags, in order.
	 * @return the header text.
	 * @throws BadRequestException if the tags are missing, or a key is invalid or occurs more than
	 * once, or a value spans more than one line.
	 */
	private static String headerFromTags(List<DialogueBranchHeader.Tag> tags)
			throws BadRequestException {
		if (tags == null)
			throw new BadRequestException("Field 'tags' is required.");
		try {
			return DialogueBranchHeader.write(tags);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("Invalid header tags: " + e.getMessage());
		}
	}

	/**
	 * Checks that a node header's {@code title} tag is the node's title. A node's title is changed
	 * only by renaming it ({@code /rename-node}), which keeps its title and its header in step, so
	 * {@code /create-node} and {@code /update-node} refuse a header whose title differs, including
	 * a header without one (an empty title).
	 *
	 * @param header the header text sent by the client.
	 * @param title  the node's title.
	 * @throws BadRequestException if the header's title differs from {@code title}.
	 */
	private static void checkHeaderTitle(String header, String title) throws BadRequestException {
		String headerTitle = Objects.requireNonNullElse(DialogueBranchHeader.get(header, "title"), "");
		if (!headerTitle.equals(title)) {
			throw new BadRequestException("The header's title ('" + headerTitle + "') must be " +
					"the node's title ('" + title + "'). Use /rename-node to change a node's title.");
		}
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

	/**
	 * Rejects writing new translation content in a translation language that is currently pending
	 * deletion, until that deletion is reverted via {@code /project/restore-translation-language}.
	 * Without this, content saved here would be silently discarded the next time the project is
	 * published (see {@code PublishService#reconcilePublishedTranslationLanguages}, which strips
	 * all draft translation content for a language pending deletion), with no warning to the
	 * author at either save or publish time.
	 *
	 * @param translationLanguage the translation language to check.
	 * @throws ConflictException if {@code translationLanguage} is pending deletion.
	 */
	static void checkNotDeleted(DBDraftTranslationLanguage translationLanguage)
			throws ConflictException {
		if (translationLanguage.getIsDeleted()) {
			throw new ConflictException("Translation language '" +
					translationLanguage.getTranslationLanguageCode() + "' is pending deletion — " +
					"restore it first.");
		}
	}

	/**
	 * Resolves the given language code to this project's registered {@link
	 * DBDraftTranslationLanguage}, or throws {@link NotFoundException} if it isn't (or is no
	 * longer) registered. Used by endpoints that write or delete translation content, where an
	 * unrecognised language code is a genuine client error — unlike {@code getTranslation}, which
	 * treats a missing/unregistered language the same as "no translation yet" rather than an error.
	 *
	 * @param project the project the language must belong to.
	 * @param languageCode the language code to resolve.
	 * @return the matching draft translation language.
	 * @throws NotFoundException if no such translation language is registered for this project.
	 */
	private DBDraftTranslationLanguage resolveTranslationLanguage(DBProject project,
			String languageCode) throws NotFoundException {
		return draftProjectService.findDraftLanguage(project, languageCode)
				.orElseThrow(() -> new NotFoundException(
						"Translation language not registered for this project: " + languageCode));
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
					return draftDialogueService.listDialogueSummaries(project);
				},
				version, response, "", Permission.DIALOGUE_LIST);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
	}

	/**
	 * Imports one or more {@code .dlb} scripts into one folder of a project, as draft content
	 * (#339). Each file becomes the draft dialogue named after the folder and the file name
	 * without {@code .dlb}. All or nothing: if any file is refused, nothing is imported, and the
	 * error lists the problems per file. Syntax errors in node bodies don't refuse a file; they
	 * are returned per file instead.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project to import into.
	 * @param folder the folder to import into; empty for the project root.
	 * @param files the {@code .dlb} files (UTF-8, at most 1 MB each, at most 100 per import).
	 * @param replace whether a file may replace the content of an existing dialogue (a dialogue
	 *                pending deletion is then restored); without it, an existing dialogue refuses
	 *                the import.
	 * @return the result per file.
	 * @throws HttpException if the project doesn't exist, any file is refused, or the user is not
	 * authorized.
	 */
	@Operation(summary = "Import .dlb scripts into a folder of a project, as draft dialogues.")
	@Parameter(name = "version", hidden = true)
	@PostMapping(value = "/import-scripts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public List<ImportedScript> importScripts(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "folder", defaultValue = "") String folder,
			@RequestParam(value = "files") List<MultipartFile> files,
			@RequestParam(value = "replace", defaultValue = "false") boolean replace
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/import-scripts [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					return scriptImportService.importScripts(project, folder, files, replace);
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
	}

	/**
	 * Exports one draft dialogue as a downloadable {@code .dlb} script (#341): its current
	 * <em>draft</em> content (what an author sees in Authoring Mode, not the published version),
	 * with its preamble and nodes in their current order. It is the counterpart of {@code
	 * /import-scripts}: the file imports again, unchanged, as a dialogue of the same name. The
	 * dialogue's translations are not part of the file.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to export, including its folder.
	 * @return the script as UTF-8 text, with a {@code Content-Disposition: attachment} header
	 * naming the file after the dialogue (without its folder) and {@code .dlb}.
	 * @throws HttpException if the project or dialogue does not exist, or the user is not
	 * authorized.
	 */
	@Operation(summary = "Export a draft dialogue as a downloadable .dlb script.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/export-dialogue")
	public ResponseEntity<byte[]> exportDialogue(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("GET /v{}/authoring/export-dialogue [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					// Dialogue names only hold letters, digits, '_' and '-' (and '/' for folders),
					// so the file name is plain ASCII.
					String fileName = dialogueName.substring(dialogueName.lastIndexOf('/') + 1)
							+ ".dlb";
					return ResponseEntity.ok()
							.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
							.header(HttpHeaders.CONTENT_DISPOSITION,
									ContentDisposition.attachment().filename(fileName).build().toString())
							.body(draftDialogueService.reconstructScript(dialogue)
									.getBytes(StandardCharsets.UTF_8));
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
	}

	/**
	 * Replaces the preamble of a draft dialogue (#345, #346): the comment lines at the start of
	 * its script. Like any other draft change, it takes effect on the next publish.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue.
	 * @param payload the new preamble: {@code //} comment lines, with blank lines left out; empty
	 *                for none.
	 * @return the preamble as stored.
	 * @throws HttpException if the project or dialogue does not exist, the dialogue is pending
	 * deletion, a line isn't a comment line, or the user is not authorized.
	 */
	@Operation(summary = "Replace the preamble (the comment lines at the start of the script) " +
			"of a draft dialogue.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/update-preamble")
	public PreamblePayload updatePreamble(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestBody PreamblePayload payload
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/authoring/update-preamble [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					try {
						return new PreamblePayload(draftDialogueService.updatePreamble(dialogue,
								payload == null ? null : payload.preamble()));
					} catch (IllegalArgumentException e) {
						throw new BadRequestException(e.getMessage());
					}
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
	public List<DraftNodeResponse> listNodes(
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
					return draftDialogueService.listNodes(dialogue).stream()
							.map(DraftNodeResponse::from).toList();
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
	public DraftNodeResponse createNode(
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
					String header = headerFromTags(payload.getTags());
					String title = Objects.requireNonNullElse(
							DialogueBranchHeader.get(header, "title"), "");
					if (!title.matches(DialogueBranchParser.NODE_NAME_REGEX)) {
						throw new BadRequestException("The 'title' tag must be a valid node " +
								"name (letters, digits, '_' and '-'), not '" + title + "'.");
					}
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					checkNotDeleted(dialogue);
					return DraftNodeResponse.from(draftDialogueService.createNode(dialogue, title,
							header, payload.getBody()));
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
	public DraftNodeResponse updateNode(
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
					String header = headerFromTags(payload.getTags());
					checkHeaderTitle(header, node.getTitle());
					return DraftNodeResponse.from(draftDialogueService.updateNode(dialogue, node,
							header, payload.getBody()));
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
	public RenameNodeResponse renameNode(
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
					DraftDialogueService.RenameResult result = draftDialogueService.renameNode(
							project, dialogue, node, newTitle, updateReferences);
					return new RenameNodeResponse(DraftNodeResponse.from(result.getNode()),
							result.getReferencesUpdated());
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
	}

	// ----------------------------------------------------------------------- //
	// -------------------- Translation Management -------------------- //
	// ----------------------------------------------------------------------- //

	/**
	 * Creates or updates the translation for a draft dialogue in a given language. The content
	 * is checked and stored re-serialised, see {@link DraftDialogueService#checkTranslationContent}
	 * (#376).
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
	 * @throws HttpException if the content isn't a valid translation, the dialogue is pending
	 * deletion, the project or dialogue does not exist, or the user is not authorized.
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
					DBDraftTranslationLanguage translationLanguage =
							resolveTranslationLanguage(project, language);
					checkNotDeleted(translationLanguage);
					String content = DraftDialogueService.requireValidTranslationContent(
							dialogueName, payload.getContent());
					return draftDialogueService.createOrUpdateTranslation(dialogue,
							translationLanguage, content);
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
					DBDraftTranslationLanguage translationLanguage =
							resolveTranslationLanguage(project, language);
					DBDraftTranslation translation = draftDialogueService
							.findTranslation(dialogue, translationLanguage)
							.orElseThrow(() -> new NotFoundException(
									"Translation not found for language: " + language));
					draftDialogueService.deleteTranslation(dialogue, translation);
					return null;
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
					// A language code that isn't (or is no longer, e.g. after a rename) registered
					// for this project is treated the same as "no translation yet" rather than an
					// error — see this method's Javadoc. Unlike updateTranslation/deleteTranslation,
					// an unrecognised code here isn't a client mistake worth rejecting; it's exactly
					// the same "nothing to show" case as a missing translation row.
					return draftProjectService.findDraftLanguage(project, language)
							.flatMap(translationLanguage ->
									draftDialogueService.findTranslation(dialogue, translationLanguage))
							.orElse(null);
				},
				version, response, "", Permission.DIALOGUE_AUTHOR);
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
				version, response, "", Permission.DIALOGUE_AUTHOR);
	}

}
