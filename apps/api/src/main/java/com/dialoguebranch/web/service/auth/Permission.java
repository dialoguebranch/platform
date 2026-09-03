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
 * The complete catalogue of distinct operations the Dialogue Branch Web Service gates on
 * authorization. Every protected end-point maps to exactly one {@link Permission}; a {@link Role}
 * holds the set of permissions it grants, and {@link AuthorizationService} answers "may this user
 * perform this operation?" by testing membership.
 *
 * <p>This enum is intentionally coarser than one-permission-per-end-point: end-points that share a
 * trust boundary today (for example all of {@code /authoring/*}) share a single permission, and
 * can be split later without disturbing callers that already request the broader one. Grouping is
 * by resource and operation, mirroring the {@code resource.operation} naming used in
 * <a href="https://github.com/dialoguebranch/platform/issues/28">#28</a>.</p>
 *
 * <p>{@link com.dialoguebranch.web.service.QueryRunner#runQuery} takes the required {@link
 * Permission} directly and enforces it through {@link AuthorizationService}; each controller passes
 * the one its end-point needs.</p>
 *
 * @author Harm op den Akker
 */
public enum Permission {

	// -------------------- Dialogue participant scope -------------------- //

	/** Run a published dialogue: {@code /dialogue/start|progress|continue|back|cancel|get-ongoing}. */
	DIALOGUE_RUN,

	/** Read the calling user's own Dialogue Branch Variables: {@code /variables/get}. */
	VARIABLE_READ_OWN,

	/** Write the calling user's own Dialogue Branch Variables: {@code /variables/set|set-single}. */
	VARIABLE_WRITE_OWN,

	/** Read the calling user's own dialogue logs: {@code /log/get-session|verify-id}. */
	LOG_READ_OWN,

	// -------------------- Editor scope -------------------- //

	/** List the dialogues in a project: {@code /dialogue/list-dialogues}, {@code /authoring/list-dialogues}. */
	DIALOGUE_LIST,

	/** Read project metadata: {@code /project/list-projects|get-project}. */
	PROJECT_READ,

	/** Inspect every variable a project's dialogues reference: {@code /variables/list-project}. */
	VARIABLE_INSPECT_PROJECT,

	/**
	 * Author draft dialogue content: all of {@code /authoring/*} — dialogue, node and translation
	 * create/update/delete/rename/restore, cross-reference lookups, and the read-side listing
	 * ({@code list-nodes}, {@code list-translatable-terms}, {@code get-translation}).
	 */
	DIALOGUE_AUTHOR,

	/** Run a dialogue against its unpublished draft: {@code /draft/start|progress|cancel|revert-variables}. */
	DIALOGUE_DRAFT_TEST,

	/** Inspect publication state: {@code /publish/list-versions|next-version|verify}. */
	PUBLISH_READ,

	// -------------------- Administrator scope -------------------- //

	/** Create a project: {@code /project/create-project}. */
	PROJECT_CREATE,

	/** Change a project's configuration: {@code /project/update-project|update-draft}. */
	PROJECT_UPDATE,

	/** Delete a project and all of its content: {@code /project/delete-project}. */
	PROJECT_DELETE,

	/** Export or import a project archive: {@code /project/export-project|import-project}. */
	PROJECT_IMPORT_EXPORT,

	/**
	 * Manage a project's translation languages:
	 * {@code /project/add-translation-language|remove-translation-language|restore-translation-language|find-language-references}.
	 */
	PROJECT_MANAGE_LANGUAGES,

	/** Publish a new version of a project's dialogues: {@code /publish/create-version}. */
	PUBLISH_CREATE,

	/** Read the service's technical/deployment information: {@code /info/technical}. */
	SERVICE_INFO_TECHNICAL,

	/**
	 * List the Dialogue Branch users known to this service (those who have run a dialogue):
	 * {@code /users}. Distinct from {@link #USER_DELEGATE} — seeing who is known and impersonating
	 * a specific user are different capabilities.
	 */
	USER_LIST,

	/** Act on behalf of another Dialogue Branch user (pass a {@code delegateUser} other than self). */
	USER_DELEGATE

}
