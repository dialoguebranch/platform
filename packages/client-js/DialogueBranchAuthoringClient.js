/* @license
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import { BaseClient } from "./BaseClient.js";
import { DialogueBranchError } from "./DialogueBranchError.js";
import { DialogueStep } from "./model/DialogueStep.js";

/**
 * Authoring client for a Dialogue Branch Web Service: project/dialogue/node/translation CRUD,
 * publishing, and testing a project's *draft* (unpublished) content. Requires the `editor` or
 * `admin` role for almost everything here — this is the Dialogue Branch Studio backend surface,
 * not what a playback-only end-user-facing app needs (see {@link DialogueBranchClient} for that,
 * the package's default export).
 */
export class DialogueBranchAuthoringClient extends BaseClient {

    // ----------------------------------------------
    // ---------- Server info / diagnostics ----------
    // ----------------------------------------------

    /**
     * Returns build/deployment diagnostics about the connected Web Service (build time,
     * configured base URL, Keycloak realm, active session count, …) — more detail than
     * {@link DialogueBranchClient#getServerInfo}, intended for an admin/diagnostic view rather
     * than a playback app.
     *
     * @returns {Promise<Object>} See the Web Service's own `GET /info/technical` documentation
     * for the full shape.
     */
    getTechnicalInfo() {
        return this._fetch(this._baseUrl + "/info/technical", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        })
        .then((response) => this._handleResponse(response));
    }

    // -----------------------------------------------
    // ---------- Project CRUD & publishing ----------
    // -----------------------------------------------

    /**
     * Lists every project on this Web Service instance. Requires the `editor`/`admin` role.
     *
     * @returns {Promise<Object[]>} One entry per project — slug, draft display name/description,
     * and its `latestVersion` (or `null` if never published).
     */
    listProjects() {
        const url = this._baseUrl + "/project/list-projects";

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

    /**
     * Creates a new, empty project (no dialogues yet, never published). Requires the `admin`
     * role.
     *
     * @param {string} slug Unique project identifier used in URLs and API calls — immutable
     * once created.
     * @param {string} displayName Human-readable project name.
     * @param {string} description Human-readable project description.
     * @param {string} sourceLanguageCode The project's source language code (e.g. `"en"`) — the
     * language dialogues are authored in.
     * @param {string} sourceLanguageName Human-readable name of the source language (e.g.
     * `"English"`).
     * @returns {Promise<Object>} The created project.
     */
    createProject(slug, displayName, description, sourceLanguageCode, sourceLanguageName) {
        const url = this._baseUrl + "/project/create-project";

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ slug, displayName, description, sourceLanguageCode, sourceLanguageName }),
        })
        .then((response) => this._handleResponse(response));
    }

    /**
     * Returns a project's full metadata — draft display name/description, source and
     * translation languages, and its `latestVersion` (or `null` if never published).
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<Object>} The project.
     */
    getProject(projectSlug) {
        const url = this._baseUrl + "/project/get-project?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Updates a project's *draft* display name/description — takes effect on the next
     * {@link publishProject} call. For also changing translation languages in the same atomic
     * request, use {@link updateProjectDraft} instead.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} displayName The new draft display name.
     * @param {string} description The new draft description.
     * @returns {Promise<Object>} The updated project.
     */
    updateProject(projectSlug, displayName, description) {
        const url = this._baseUrl + "/project/update-project?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ displayName, description }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Applies a whole "Save Draft" batch — display name/description plus translation-language
     * add/remove/rename — in one atomic request: either all of it lands, or (if the server finds
     * a problem with any part of the batch) none of it does.
     *
     * @param {string} projectSlug The project's slug.
     * @param {Object} draft
     * @param {string} [draft.displayName] The new draft display name.
     * @param {string} [draft.description] The new draft description.
     * @param {string[]} [draft.removeLanguageIds] Ids of translation languages to remove.
     * @param {{translationLanguageName: string, translationLanguageCode: string}[]} [draft.addLanguages]
     * New translation languages to add.
     * @param {{id: string, translationLanguageName: string, translationLanguageCode: string}[]} [draft.updateLanguages]
     * Existing translation languages to rename.
     * @returns {Promise<Object>} The updated project.
     */
    updateProjectDraft(projectSlug, { displayName, description, removeLanguageIds, addLanguages, updateLanguages }) {
        const url = this._baseUrl + "/project/update-draft?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ displayName, description, removeLanguageIds, addLanguages, updateLanguages }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Permanently deletes a project — all draft and published content. Requires the `admin`
     * role. Cannot be undone.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<void>}
     */
    deleteProject(projectSlug) {
        const url = this._baseUrl + "/project/delete-project?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    // Deliberately bypasses _fetch/_handleResponse: those read the response body as text (to log
    // it) and reconstruct a new Response from that string, which would corrupt binary content —
    // downloads stay on the raw fetch Response and read it as a blob instead. Shared by
    // exportProject and exportDialogue.
    async _downloadBlob(url) {
        const response = await this._fetchImpl(url, {
            method: "GET",
            credentials: this._credentials,
        });

        if (response.status === 401) {
            return this._unauthorizedResult();
        }

        if (!response.ok) {
            const body = await response.json().catch(() => null);
            return Promise.reject(new DialogueBranchError(
                body?.message ?? `The server returned an error (${response.status}).`,
                {
                    status: response.status,
                    statusText: response.statusText,
                    code: body?.code ?? null,
                    fieldErrors: body?.fieldErrors ?? [],
                    errors: body?.errors ?? null,
                },
            ));
        }

        return response.blob();
    }

    /**
     * Downloads a project's currently *published* content (not draft) as a `.zip` archive.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<Blob>} The archive.
     */
    exportProject(projectSlug) {
        return this._downloadBlob(
            this._baseUrl + "/project/export-project?projectSlug=" + encodeURIComponent(projectSlug));
    }

    /**
     * Imports a new project from a previously {@link exportProject}-ed `.zip` archive. Requires
     * the `admin` role.
     *
     * @param {File} file A `.zip` archive as previously produced by {@link exportProject} (e.g.
     * from a file input) — no `Content-Type` header is set, so the browser fills in the
     * multipart boundary itself.
     * @returns {Promise<Object>} The created project.
     */
    importProject(file) {
        const url = this._baseUrl + "/project/import-project";
        const formData = new FormData();
        formData.append('file', file);

        return this._fetch(url, {
            method: "POST",
            body: formData,
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Adds a *draft* translation language to a project — takes effect on the next
     * {@link publishProject} call.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} translationLanguageName Human-readable language name (e.g. `"Dutch"`).
     * @param {string} translationLanguageCode Language code (e.g. `"nl-NL"`).
     * @returns {Promise<Object>} The updated project.
     */
    addTranslationLanguage(projectSlug, translationLanguageName, translationLanguageCode) {
        const url = this._baseUrl + "/project/add-translation-language?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ translationLanguageName, translationLanguageCode }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Soft-deletes a *draft* translation language — reversible via {@link restoreTranslationLanguage}
     * until the next {@link publishProject} call, at which point the language (and any draft
     * content still in it) is actually removed. Check {@link findLanguageReferences} first if you
     * want to warn the user about content that will be lost.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} translationLanguageId The translation language's id.
     * @returns {Promise<Object>} The updated project.
     */
    removeTranslationLanguage(projectSlug, translationLanguageId) {
        const url = this._baseUrl + "/project/remove-translation-language?projectSlug=" + encodeURIComponent(projectSlug) + "&translationLanguageId=" + encodeURIComponent(translationLanguageId);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Reverts a pending deletion previously made via {@link removeTranslationLanguage}. No effect
     * once the project has been published since the removal (the draft row is gone for good by
     * then).
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} translationLanguageId The translation language's id.
     * @returns {Promise<Object>} The updated project.
     */
    restoreTranslationLanguage(projectSlug, translationLanguageId) {
        const url = this._baseUrl + "/project/restore-translation-language?projectSlug=" + encodeURIComponent(projectSlug) + "&translationLanguageId=" + encodeURIComponent(translationLanguageId);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Lists the draft dialogues that currently have content in the given draft translation
     * language — use to warn the user what will be affected before calling
     * {@link removeTranslationLanguage}.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} translationLanguageId The translation language's id.
     * @returns {Promise<Object[]>} The dialogues with content in that language.
     */
    findLanguageReferences(projectSlug, translationLanguageId) {
        const url = this._baseUrl + "/project/find-language-references?projectSlug=" + encodeURIComponent(projectSlug)
            + "&translationLanguageId=" + encodeURIComponent(translationLanguageId);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Discards all of a project's draft changes: its draft becomes identical to its latest
     * published version again (display name and description, translation languages, and every
     * dialogue; dialogues that were never published are removed). Cannot be undone. Needs the
     * permission to publish; refused for a project that was never published.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<{dialoguesReverted: number, dialoguesRemoved: number}>} How many
     * dialogues were brought back to their published version, and how many were removed.
     */
    discardAllDraftChanges(projectSlug) {
        const url = this._baseUrl + "/project/discard-draft-changes?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Validates all of the project's draft dialogues and, if valid, publishes them as a new,
     * immutable project version — replacing what's currently live and cannot be undone. Consider
     * calling {@link verifyProject} first to check for errors without actually publishing.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<{success: boolean, version: number|null, errors: Object[]}>} `success`
     * is `false` (with no new version created) if any dialogue failed validation — see `errors`.
     */
    publishProject(projectSlug) {
        const url = this._baseUrl + "/publish/create-version?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Validates the project's current draft exactly as {@link publishProject} would, but without
     * actually publishing anything — use to show validation errors before committing to a
     * publish.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<{valid: boolean, errors: Object[]}>}
     */
    verifyProject(projectSlug) {
        const url = this._baseUrl + "/publish/verify?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Returns the version number the next {@link publishProject} call would create, without
     * creating it — useful for a confirmation message like "this will publish version 4".
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<number>} The next version number.
     */
    getNextProjectVersion(projectSlug) {
        const url = this._baseUrl + "/publish/next-version?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        }).then((response) => this._handleResponse(response));
    }

    // -----------------------------------------------------------------
    // ---------- Draft dialogue test-execution (ephemeral) ----------
    // -----------------------------------------------------------------

    /**
     * Lists the project's *draft* dialogues — including ones only in draft (never published) and
     * ones pending deletion — unlike {@link DialogueBranchClient#listDialogues}, which only
     * lists published dialogue names.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<Object[]>} One entry per draft dialogue: `{ name, isNew, isChanged,
     * isDeleted, updatedAt, nodeCount, preamble, translatedInto, … }`, where `preamble` is
     * the comment lines at the start of its script (`//` lines joined with `\n`, or `""` for
     * none) and `translatedInto` the codes of the project's translation languages it already has
     * a translation in.
     */
    listDraftDialogues(projectSlug) {
        const url = this._baseUrl + "/authoring/list-dialogues?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

    /**
     * Imports `.dlb` scripts into one folder of a project, as draft dialogues: each file becomes
     * the dialogue named after the folder and its file name without `.dlb`. All or nothing: if any
     * file is refused, nothing is imported, and the rejection's {@link DialogueBranchError}
     * carries the problems per file name in `errors`.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} folder The folder to import into (e.g. `"onboarding"`), or `""` for the
     * project root.
     * @param {File[]|Blob[]} files The `.dlb` files (UTF-8, at most 1 MB each, at most 100).
     * @param {{replace?: boolean}} [options] `replace`: let a file replace the content of an
     * existing dialogue (a dialogue pending deletion is then restored); without it, an existing
     * dialogue refuses the import.
     * @returns {Promise<{fileName: string, dialogueName: string, replaced: boolean,
     * parseErrors: string[]}[]>} The result per file. `parseErrors` lists syntax errors: those
     * don't refuse a file, and are reported again when publishing.
     */
    importScripts(projectSlug, folder, files, { replace = false } = {}) {
        const url = this._baseUrl + "/authoring/import-scripts";
        const formData = new FormData();
        formData.append('projectSlug', projectSlug);
        formData.append('folder', folder ?? '');
        formData.append('replace', String(!!replace));
        for (const file of files) formData.append('files', file, file.name);

        return this._fetch(url, {
            method: "POST",
            body: formData,
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Imports translation files (`.json`) into one translation language of a project, as draft
     * translations: each file becomes the translation of the existing dialogue named after the
     * folder and its file name without `.json`. Each file is stored re-serialised, like
     * {@link updateDraftTranslation} stores it. All or nothing: if any file is refused (not a
     * valid translation file, no translations, a dialogue that doesn't exist or is pending
     * deletion, or an existing translation without `replace`), nothing is imported, and the
     * rejection's {@link DialogueBranchError} carries the problems per file name in `errors`.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} language The code of the translation language to import into.
     * @param {string} folder The folder of the dialogues (e.g. `"onboarding"`), or `""` for the
     * project root.
     * @param {File[]|Blob[]} files The translation files (UTF-8, at most 1 MB each, at most 100).
     * @param {{replace?: boolean}} [options] `replace`: let a file replace a dialogue's existing
     * translation in this language; without it, an existing translation refuses the import.
     * @returns {Promise<{fileName: string, dialogueName: string, replaced: boolean,
     * unmatchedEntries: number|null, untranslatedTerms: number|null}[]>} The result per file:
     * how many entries match no translatable term of the dialogue (kept anyway), and how many of
     * its terms have no entry; both `null` when the dialogue's draft doesn't parse.
     */
    importTranslations(projectSlug, language, folder, files, { replace = false } = {}) {
        const url = this._baseUrl + "/authoring/import-translations";
        const formData = new FormData();
        formData.append('projectSlug', projectSlug);
        formData.append('language', language);
        formData.append('folder', folder ?? '');
        formData.append('replace', String(!!replace));
        for (const file of files) formData.append('files', file, file.name);

        return this._fetch(url, {
            method: "POST",
            body: formData,
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Downloads one dialogue's current *draft* content (what an author sees in Authoring Mode,
     * not the published version) as a `.dlb` script, with its preamble and nodes in their current
     * order. The counterpart of {@link importScripts}: the file imports again, unchanged, as a
     * dialogue of the same name. The dialogue's translations are not part of it.
     *
     * Name the file after the dialogue's last name segment (the part after any folder) plus
     * `.dlb`, so it imports back under the same name.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name, including its folder (e.g.
     * `onboarding/welcome`).
     * @returns {Promise<Blob>} The script, as UTF-8 text.
     */
    exportDialogue(projectSlug, dialogueName) {
        return this._downloadBlob(this._baseUrl + "/authoring/export-dialogue?projectSlug="
            + encodeURIComponent(projectSlug) + "&dialogueName=" + encodeURIComponent(dialogueName));
    }

    /**
     * Replaces a draft dialogue's preamble: the comment lines at the start of its script. Blank
     * lines are left out; every other line must be a `//` comment line, or the request is refused.
     * Like any draft change, it takes effect on the next publish.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @param {string} preamble The new preamble: `//` comment lines joined with `\n`, or `""`
     * for none.
     * @returns {Promise<{preamble: string}>} The preamble as stored.
     */
    updateDraftPreamble(projectSlug, dialogueName, preamble) {
        const url = this._baseUrl + "/authoring/update-preamble?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ preamble }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Starts an ephemeral test session of a dialogue's current *draft* (unpublished) content —
     * the equivalent of {@link DialogueBranchClient#startDialogue} for trying out in-progress
     * edits, e.g. in a visual editor. Nothing is logged as a real dialogue session.
     *
     * @param {Object} options
     * @param {string} options.projectSlug The project's slug.
     * @param {string} options.dialogueName The dialogue's name.
     * @param {string} options.language The language code to test in.
     * @param {string} [options.timeZone] The caller's IANA time zone (e.g. `"Europe/Lisbon"`).
     * This client never infers it — pass it explicitly (e.g.
     * `Intl.DateTimeFormat().resolvedOptions().timeZone` in a browser).
     * @param {string} [options.startNodeId] Start at a specific node instead of the dialogue's
     * default start node.
     * @returns {Promise<{draftSessionId: string, dialogueStep: DialogueStep}>} `draftSessionId`
     * identifies this ephemeral test session for {@link progressDraftDialogue}/
     * {@link cancelDraftDialogue}/{@link revertDraftVariables}.
     */
    startDraftDialogue({ projectSlug, dialogueName, language, timeZone, startNodeId }) {
        let url = this._baseUrl + "/draft/start";

        url += "?projectSlug=" + encodeURIComponent(projectSlug);
        url += "&dialogueName=" + encodeURIComponent(dialogueName);
        url += "&language=" + encodeURIComponent(language);
        if (timeZone) url += "&timeZone=" + encodeURIComponent(timeZone);
        if (startNodeId) url += "&startNodeId=" + encodeURIComponent(startNodeId);
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((json) => ({
            draftSessionId: json.draftSessionId,
            dialogueStep: DialogueStep.fromJSON(json.dialogueMessage),
        }));
    }

    /**
     * Advances a draft-test session by one step — the {@link startDraftDialogue} equivalent of
     * {@link DialogueBranchClient#progressDialogue}.
     *
     * @param {Object} options
     * @param {string} options.draftSessionId The draft-test session's id, from
     * {@link startDraftDialogue}.
     * @param {number} options.replyId The id of the reply the user selected.
     * @param {Object} [options.inputValues] If the selected reply had one or more `<<input>>`
     * commands, the values the user provided for them (see
     * {@link DialogueBranchClient#progressDialogue}'s `inputValues`).
     * @param {string} [options.timeZone] The caller's IANA time zone. See
     * {@link startDraftDialogue}'s `timeZone`.
     * @returns {Promise<DialogueStep|null>} The next step, or `null` if the dialogue ended.
     */
    progressDraftDialogue({ draftSessionId, replyId, inputValues = null, timeZone }) {
        let url = this._baseUrl + "/draft/progress";

        url += "?draftSessionId=" + encodeURIComponent(draftSessionId);
        url += "&replyId=" + replyId;
        if (timeZone) url += "&timeZone=" + encodeURIComponent(timeZone);
        url += this._delegateParam;

        const body = inputValues ? JSON.stringify(inputValues) : null;
        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            ...(body != null ? { body } : {})
        }, body)
        .then((response) => this._handleResponse(response))
        .then((json) => json.value ? DialogueStep.fromJSON(json.value) : null);
    }

    /**
     * Explicitly ends an ephemeral draft-test session started via {@link startDraftDialogue}.
     *
     * @param {string} draftSessionId The draft-test session's id.
     * @returns {Promise<void>}
     */
    cancelDraftDialogue(draftSessionId) {
        const url = this._baseUrl + "/draft/cancel?draftSessionId=" + encodeURIComponent(draftSessionId)
            + this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

    /**
     * Reverts any variable changes made during a draft-test session back to what they were
     * before it started — so trying out a dialogue in the editor doesn't leave the tester's real
     * stored variable values altered.
     *
     * @param {Object} options
     * @param {string} options.draftSessionId The draft-test session's id.
     * @param {string} [options.timeZone] The caller's IANA time zone. See
     * {@link startDraftDialogue}'s `timeZone`.
     * @returns {Promise<void>}
     */
    revertDraftVariables({ draftSessionId, timeZone }) {
        let url = this._baseUrl + "/draft/revert-variables?draftSessionId=" + encodeURIComponent(draftSessionId);
        if (timeZone) url += "&timeZone=" + encodeURIComponent(timeZone);
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

    // -----------------------------------------------------------------
    // ---------- Authoring (draft dialogue & node CRUD) ----------
    // -----------------------------------------------------------------

    /**
     * Creates a new, empty *draft* dialogue (no nodes yet) in a project.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} name The new dialogue's name.
     * @returns {Promise<Object>} The created draft dialogue.
     */
    createDraftDialogue(projectSlug, name) {
        const url = this._baseUrl + "/authoring/create-dialogue?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Soft-deletes a draft dialogue: marks it pending deletion, reversible via
     * {@link restoreDraftDialogue} until the project is next {@link publishProject|published}.
     * Consider checking {@link findDialogueReferences} first to warn about dangling `[[...]]`
     * links elsewhere in the project.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @returns {Promise<void>}
     */
    deleteDraftDialogue(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/delete-dialogue?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Discards a draft dialogue's changes: makes it identical to its latest published version
     * again (nodes and their layout, preamble, translations, and its published name if it was
     * renamed), so it is no longer marked as changed. Refused for a dialogue that was never
     * published or is pending deletion, when another dialogue has taken its published name, or
     * when a translation language was renamed since it was published.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's (draft) name.
     * @returns {Promise<{dialogueName: string, discardedName: string}>} The dialogue's name now
     * (its published name), and the name it had before.
     */
    discardDraftDialogueChanges(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/discard-dialogue-changes?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Reverts a pending deletion previously made via {@link deleteDraftDialogue}.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @returns {Promise<void>}
     */
    restoreDraftDialogue(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/restore-dialogue?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Scans the whole project for `[[...]]` reply links that reference the given dialogue (any
     * node within it) — use before {@link renameDraftDialogue} to preview the rename's blast
     * radius, or before {@link deleteDraftDialogue} to warn about links that would dangle.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @returns {Promise<Object[]>} The referencing nodes, grouped by dialogue.
     */
    findDialogueReferences(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/find-dialogue-references?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Renames a draft dialogue.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's current name.
     * @param {string} newName The dialogue's new name.
     * @param {boolean} updateReferences Whether to also rewrite every `[[...]]` reply link
     * elsewhere in the project that pointed at the old name (see {@link findDialogueReferences}
     * to preview which links those are). If `false`, those links are left pointing at the old
     * name and will dangle.
     * @returns {Promise<void>}
     */
    renameDraftDialogue(projectSlug, dialogueName, newName, updateReferences) {
        const url = this._baseUrl + "/authoring/rename-dialogue?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&newName=" + encodeURIComponent(newName)
            + "&updateReferences=" + !!updateReferences;

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Lists a draft dialogue's nodes: the editable content, not a parsed/executable form. Each
     * node is `{ id, title, tags, body, createdAt, updatedAt }`, where `tags` is its header as an
     * ordered list of `{ key, value }` pairs (the reserved tags `title`/`speaker`/`position`/
     * `colorId`/`comment`, plus any custom ones), and `body` its raw `.dlb` body text.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @returns {Promise<Object[]>} The dialogue's nodes.
     */
    listDraftNodes(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/list-nodes?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Creates a new node in a draft dialogue. Its title is its `title` tag, which must be a valid
     * node name, unique within the dialogue.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @param {{key: string, value: string}[]} tags The node's header as an ordered list of tags
     * (see {@link listDraftNodes}). Keys consist of letters, digits, `_` and `-`, may each occur
     * once, and values are a single line.
     * @param {string} body The node's raw `.dlb` body text (statement + `[[reply]]` lines,
     * commands, …).
     * @returns {Promise<Object>} The created node (see {@link listDraftNodes}).
     */
    createDraftNode(projectSlug, dialogueName, tags, body) {
        const url = this._baseUrl + "/authoring/create-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ tags, body }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Updates an existing node's header tags and body. Both are replaced as a whole: send all of
     * the node's tags, not only the changed ones.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @param {string} nodeTitle The node's current title.
     * @param {{key: string, value: string}[]} tags The node's complete new header tags (see
     * {@link createDraftNode}). Its `title` tag must stay the node's current title: to rename the
     * node, use {@link renameDraftNode}.
     * @param {string} body The node's new raw `.dlb` body text.
     * @returns {Promise<Object>} The updated node (see {@link listDraftNodes}).
     */
    updateDraftNode(projectSlug, dialogueName, nodeTitle, tags, body) {
        const url = this._baseUrl + "/authoring/update-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&nodeTitle=" + encodeURIComponent(nodeTitle);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ tags, body }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Permanently deletes a node from a draft dialogue. Unlike dialogue deletion, this is
     * immediate — there's no soft-delete/restore for individual nodes. Consider checking
     * {@link findNodeReferences} first to warn about `[[...]]` links elsewhere that would dangle.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @param {string} nodeTitle The node's title.
     * @returns {Promise<void>}
     */
    deleteDraftNode(projectSlug, dialogueName, nodeTitle) {
        const url = this._baseUrl + "/authoring/delete-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&nodeTitle=" + encodeURIComponent(nodeTitle);

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Scans the whole project for `[[...]]` reply links that reference the given node — use
     * before {@link renameDraftNode} to preview the rename's blast radius, or before
     * {@link deleteDraftNode} to warn about links that would dangle.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue containing the node.
     * @param {string} nodeTitle The node's title.
     * @returns {Promise<Object[]>} The referencing nodes, grouped by dialogue.
     */
    findNodeReferences(projectSlug, dialogueName, nodeTitle) {
        const url = this._baseUrl + "/authoring/find-node-references?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&nodeTitle=" + encodeURIComponent(nodeTitle);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Renames a node within a draft dialogue.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue containing the node.
     * @param {string} oldTitle The node's current title.
     * @param {string} newTitle The node's new title.
     * @param {boolean} updateReferences Whether to also rewrite every `[[...]]` reply link
     * elsewhere in the dialogue (or project, for cross-dialogue links) that pointed at the old
     * title (see {@link findNodeReferences} to preview which links those are). If `false`, those
     * links are left pointing at the old title and will dangle.
     * @returns {Promise<{node: Object, referencesUpdated: number}>} The renamed node (see
     * {@link listDraftNodes}) and how many references to it were updated.
     */
    renameDraftNode(projectSlug, dialogueName, oldTitle, newTitle, updateReferences) {
        const url = this._baseUrl + "/authoring/rename-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&oldTitle=" + encodeURIComponent(oldTitle)
            + "&newTitle=" + encodeURIComponent(newTitle)
            + "&updateReferences=" + !!updateReferences;

        return this._fetch(url, {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Extracts every translatable term (source-language text segment) from a dialogue's current
     * draft content — the source-language column of a translation-editing UI.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @returns {Promise<Object[]>} The extracted terms.
     */
    listTranslatableTerms(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/list-translatable-terms?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Returns a dialogue's current draft translation content for one language — the terms from
     * {@link listTranslatableTerms} paired with whatever translated text has been entered for
     * them so far (untranslated terms included, with no translation yet).
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @param {string} language The translation language's code.
     * @returns {Promise<Object>} The translation content for that language.
     */
    getDraftTranslation(projectSlug, dialogueName, language) {
        const url = this._baseUrl + "/authoring/get-translation?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&language=" + encodeURIComponent(language);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Replaces a dialogue's entire draft translation content for one language — always a full
     * replace, matching the shape returned by {@link getDraftTranslation}, not a per-term patch.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @param {string} language The translation language's code.
     * @param {string} content The new translation content, JSON-serialized (as sent to the Web
     * Service — see the call sites' `JSON.stringify(...)` if building this by hand).
     * @returns {Promise<void>}
     */
    updateDraftTranslation(projectSlug, dialogueName, language, content) {
        const url = this._baseUrl + "/authoring/update-translation?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&language=" + encodeURIComponent(language);

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ content }),
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Looks up known Dialogue Branch users in the caller's realm whose username contains the
     * given fragment — used to resolve a username to the `subject` that a client's `delegateUser`
     * (both this class's `progressDraftDialogue`/etc. and
     * {@link DialogueBranchClient}'s `delegateUser`) needs, e.g. for a "run as this user"
     * picker. Requires the `admin` role.
     *
     * @param {Object} [options]
     * @param {string} [options.usernameFragment] Substring to filter usernames by. Omit (or pass
     * an empty string) to list all known users.
     * @param {number} [options.page] Zero-based page index. Defaults to the Web Service's own
     * default (currently `0`).
     * @param {number} [options.pageSize] Page size. Defaults to the Web Service's own default
     * (currently `50`, capped server-side regardless of what's requested).
     * @returns {Promise<{username: string, subject: string}[]>} The matching page of users,
     * ordered by username. Only includes users the Web Service has actually seen run a dialogue
     * before — a Keycloak account that has never authenticated against it won't appear.
     */
    listUsers({ usernameFragment, page, pageSize } = {}) {
        let url = this._baseUrl + "/users?username=" + encodeURIComponent(usernameFragment ?? "");
        if (page != null) url += "&page=" + page;
        if (pageSize != null) url += "&pageSize=" + pageSize;

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => Array.isArray(data) ? data : []);
    }

    /**
     * Returns the sorted list of variable names referenced anywhere in the given project's
     * dialogues (read or written), regardless of whether any user has a stored value for them —
     * a static, authoring-time view, unlike {@link DialogueBranchClient#getVariables}'s runtime
     * per-user values.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<{name: string, read: boolean, written: boolean}[]>} The referenced
     * variable names.
     */
    listProjectVariables(projectSlug) {
        const url = this._baseUrl + "/variables/list-project?projectSlug="
            + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => Array.isArray(data) ? data : []);
    }

    /**
     * Returns the variables the project's configured External Variable Service reports as
     * supported, proxied through live by the Web Service (never cached).
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<Object[]>} The supported variables. Rejects if no External Variable
     * Service is configured for this deployment, or it could not be reached — treat that as "no
     * EVS info available" rather than a user-facing error, since not every deployment configures
     * one.
     */
    listSupportedVariables(projectSlug) {
        const url = this._baseUrl + "/variables/list-supported?projectSlug="
            + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => Array.isArray(data) ? data : []);
    }

}
