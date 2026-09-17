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
import { Variable } from "./model/Variable.js";
import { DialogueStep } from "./model/DialogueStep.js";
import { ServerInfo } from "./model/ServerInfo.js";
import { OngoingDialogue } from "./model/OngoingDialogue.js";

/**
 * Playback-only client for a Dialogue Branch Web Service: running dialogues (published content)
 * and reading/writing the current user's variables. No project/dialogue authoring — see
 * {@link DialogueBranchAuthoringClient} for that (`@dialoguebranch/client-js/authoring`).
 */
export class DialogueBranchClient extends BaseClient {

    /**
     * Ends the Web Service's own server-side session for the current user (a separate concept
     * from any session cookie your app itself uses to authenticate — this just tells the
     * Dialogue Branch Web Service the user is done).
     *
     * @returns {Promise<void>} Resolves once the session is ended.
     */
    logout() {
        return this._fetch(this._baseUrl + "/auth/logout", {
            method: "POST",
        }).then((response) => this._handleResponse(response));
    }

    /**
     * Returns general information about the connected Web Service — its version, and other
     * details not specific to any project or dialogue. Doesn't require any particular role.
     *
     * @returns {Promise<ServerInfo>} The server info.
     */
    getServerInfo() {
        return this._fetch(this._baseUrl + "/info/all", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        })
        .then((response) => this._handleResponse(response))
        .then((json) => ServerInfo.fromJSON(json));
    }

    /**
     * Lists the published dialogues available in a project. Requires the `editor`/`admin` role
     * (a playback-only consumer typically already knows which dialogue it wants to start, so
     * this is more for a client that offers dialogue discovery/selection than a strict end-user
     * playback UI) — see the Web Service's own docs for direct API clients.
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<Object>} `{ dialogueNames: string[] }`.
     */
    listDialogues(projectSlug) {
        const url = this._baseUrl + "/dialogue/list-dialogues?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

    /**
     * Starts a new session of a published dialogue, from its default start node (or `startNodeId`
     * if given). This is the usual entry point for playing a dialogue.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name (as returned by {@link listDialogues}).
     * @param {string} language The language code to run the dialogue in (e.g. `"en"`), matching
     * one of the project's source/translation languages.
     * @param {string} [startNodeId] Start at a specific node instead of the dialogue's default
     * start node.
     * @returns {Promise<DialogueStep>} The first step of the dialogue.
     */
    startDialogue(projectSlug, dialogueName, language, startNodeId) {
        var url = this._baseUrl + "/dialogue/start";

        url += "?projectSlug="+encodeURIComponent(projectSlug);
        url += "&dialogueName="+encodeURIComponent(dialogueName);
        url += "&language="+encodeURIComponent(language);
        url += "&timeZone="+this._timeZone;
        if (startNodeId) url += "&startNodeId=" + encodeURIComponent(startNodeId);
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((json) => DialogueStep.fromJSON(json));
    }

    /**
     * Advances a dialogue session by one step, submitting the reply the user selected.
     *
     * @param {string} loggedDialogueId The dialogue session's id, from the previous
     * {@link DialogueStep} (`step.loggedDialogueId`).
     * @param {number} loggedInteractionIndex The previous step's interaction index
     * (`step.loggedInteractionIndex`).
     * @param {number} replyId The id of the reply the user selected (`reply.replyId`).
     * @param {Object} [inputValues] If the selected reply had one or more `<<input>>` commands,
     * the values the user provided for them — an object mapping Dialogue Branch variable names to
     * values, sent as the request body and stored by the Web Service before progressing.
     * @returns {Promise<DialogueStep|null>} The next step, or `null` if the dialogue ended.
     */
    progressDialogue(loggedDialogueId, loggedInteractionIndex, replyId, inputValues = null) {
        var url = this._baseUrl + "/dialogue/progress";

        url += "?loggedDialogueId="+encodeURIComponent(loggedDialogueId);
        url += "&loggedInteractionIndex="+loggedInteractionIndex;
        url += "&replyId="+replyId;
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
     * Resumes the user's most recent interrupted (not explicitly cancelled or ended) session of
     * the given dialogue, picking up at the step it was left on — see also
     * {@link getOngoingDialogue}, which checks whether such a session exists without resuming it.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} dialogueName The dialogue's name.
     * @returns {Promise<DialogueStep|null>} The step the session was left on, or `null` if there
     * is no ongoing session for this dialogue.
     */
    continueDialogue(projectSlug, dialogueName) {
        var url = this._baseUrl + "/dialogue/continue";

        url += "?projectSlug="+encodeURIComponent(projectSlug);
        url += "&dialogueName="+encodeURIComponent(dialogueName);
        url += "&timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => {
            var dialogueData = data?.value;
            if (dialogueData && 'dialogue' in dialogueData) {
                // Create a DialogueStep object from the received data
                return DialogueStep.fromJSON(dialogueData);
            }
            return null;
        });
    }

    /**
     * Explicitly ends an in-progress dialogue session before it reaches a natural end node —
     * e.g. the user navigated away. A cancelled session can no longer be resumed via
     * {@link continueDialogue}.
     *
     * @param {string} loggedDialogueId The dialogue session's id.
     * @returns {Promise<void>}
     */
    cancelDialogue(loggedDialogueId) {
        let url = this._baseUrl + "/dialogue/cancel?loggedDialogueId=" + encodeURIComponent(loggedDialogueId);
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

    /**
     * Returns all of the current user's stored Dialogue Branch variable values for a project —
     * variables are scoped per `(user, project)`, so this never includes another project's
     * values. See {@link setVariable} to write one; there is no bulk-write equivalent on this
     * class (see `DialogueBranchAuthoringClient.listProjectVariables` for the separate, static
     * "which variable names does this project's content reference" question).
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<Variable[]>} The stored variables — empty if none are set yet.
     */
    getVariables(projectSlug) {
        var url = this._baseUrl + "/variables/get";

        url += "?projectSlug="+encodeURIComponent(projectSlug);
        url += "&timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => (data ?? []).map((entry) => Variable.fromJSON(entry)))
    }

    /**
     * Checks whether the current user has an interrupted (not cancelled or ended) session of any
     * dialogue in the given project, without resuming it — use {@link continueDialogue} to
     * actually pick it back up, e.g. after asking the user "you have an unfinished conversation,
     * continue it?".
     *
     * @param {string} projectSlug The project's slug.
     * @returns {Promise<OngoingDialogue|null>} Information about the ongoing session, or `null`
     * if there is none.
     */
    getOngoingDialogue(projectSlug) {
        let url = this._baseUrl + "/dialogue/get-ongoing";
        url += "?projectSlug=" + encodeURIComponent(projectSlug);
        url += "&timeZone=" + this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "GET",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => data?.value ? OngoingDialogue.fromJSON(data.value) : null);
    }

    /**
     * Sets (or clears) a single Dialogue Branch variable value for the current user, in the given
     * project.
     *
     * @param {string} projectSlug The project's slug.
     * @param {string} variableName The variable's name (without the leading `$`).
     * @param {string|null} variableValue The value to set, or `null`/omitted to clear it.
     * @returns {Promise<void>}
     */
    setVariable(projectSlug, variableName, variableValue) {
        var url = this._baseUrl + "/variables/set-single";

        url += "?projectSlug="+encodeURIComponent(projectSlug);
        url += "&name="+encodeURIComponent(variableName);
        if(variableValue != null) url += "&value="+encodeURIComponent(variableValue);
        url += "&timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        })
        .then((response) => this._handleResponse(response));
    }

}
