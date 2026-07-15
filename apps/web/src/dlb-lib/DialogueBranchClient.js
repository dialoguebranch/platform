/* @license
 *
 *                Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
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

import { AutoForwardReply } from "./model/AutoForwardReply";
import { BasicReply } from "./model/BasicReply";
import { DialogueStep } from "./model/DialogueStep";
import { Segment } from "./model/Segment";
import { Statement } from "./model/Statement";
import { Variable } from "./model/Variable";
import { logApiCall } from "../composables/debug-log.js";

export class DialogueBranchClient {

    // TODO: Create a generic "call protected end-point" function that will inject the accessToken
    // in the header, and will automatically attempt access token refresh upon "expired token" errors.

    constructor(baseUrl, accessTokenFn) {
        this._baseUrl = baseUrl;
        this._accessTokenFn = typeof accessTokenFn === 'function' ? accessTokenFn : () => accessTokenFn;
        this._timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
        this.delegateUser = null;
    }

    get _accessToken() {
        return this._accessTokenFn();
    }

    get _delegateParam() {
        return this.delegateUser ? '&delegateUser=' + encodeURIComponent(this.delegateUser) : '';
    }

    onUnauthorized(onUnauthorized) {
        this._onUnauthorized = onUnauthorized;
    }

    logout() {
        return this._fetch(this._baseUrl + "/auth/logout", {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    getServerInfo() {
        return this._fetch(this._baseUrl + "/info/all", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        })
        .then((response) => this._handleResponse(response));
    }

    listProjects() {
        const url = this._baseUrl + "/project/list-projects";

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    createProject(slug, displayName, description, defaultLanguageCode, defaultLanguageName) {
        const url = this._baseUrl + "/project/create-project";

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ slug, displayName, description, defaultLanguageCode, defaultLanguageName }),
        })
        .then((response) => this._handleResponse(response));
    }

    getProject(projectSlug) {
        const url = this._baseUrl + "/project/get-project?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: { 'Authorization': 'Bearer ' + this._accessToken, "Content-Type": "application/json" },
        }).then((response) => this._handleResponse(response));
    }

    updateProject(projectSlug, displayName, description) {
        const url = this._baseUrl + "/project/update-project?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken, "Content-Type": "application/json" },
            body: JSON.stringify({ displayName, description }),
        }).then((response) => this._handleResponse(response));
    }

    deleteProject(projectSlug) {
        const url = this._baseUrl + "/project/delete-project?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    addTranslationLanguage(projectSlug, translationLanguageName, translationLanguageCode) {
        const url = this._baseUrl + "/project/add-translation-language?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken, "Content-Type": "application/json" },
            body: JSON.stringify({ translationLanguageName, translationLanguageCode }),
        }).then((response) => this._handleResponse(response));
    }

    removeTranslationLanguage(projectSlug, translationLanguageId) {
        const url = this._baseUrl + "/project/remove-translation-language?projectSlug=" + encodeURIComponent(projectSlug) + "&translationLanguageId=" + encodeURIComponent(translationLanguageId);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    // Validates all of the project's draft dialogues and, if valid, publishes them as a new,
    // immutable project version. Returns { success, version, errors } — see PublishService.java.
    publishProject(projectSlug) {
        const url = this._baseUrl + "/publish/create-version?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        }).then((response) => this._handleResponse(response));
    }

    // Validates the project's current draft exactly as publishProject() would, but without
    // actually publishing anything. Returns { valid, errors } — see PublishService.VerifyResult.
    verifyProject(projectSlug) {
        const url = this._baseUrl + "/publish/verify?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        }).then((response) => this._handleResponse(response));
    }

    // Returns the version number the next publishProject() call would create, without creating it.
    getNextProjectVersion(projectSlug) {
        const url = this._baseUrl + "/publish/next-version?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        }).then((response) => this._handleResponse(response));
    }

    listDialogues(projectSlug) {
        const url = this._baseUrl + "/dialogue/list-dialogues?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    startDialogue(projectSlug, dialogueName, language) {
        var url = this._baseUrl + "/dialogue/start";

        url += "?projectSlug="+encodeURIComponent(projectSlug);
        url += "&dialogueName="+dialogueName;
        url += "&language="+language;
        url += "&timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((json) => this.createDialogueStepObject(json));
    }

    progressDialogue(loggedDialogueId, loggedInteractionIndex, replyId) {
        var url = this._baseUrl + "/dialogue/progress";

        url += "?loggedDialogueId="+loggedDialogueId;
        url += "&loggedInteractionIndex="+loggedInteractionIndex;
        url += "&replyId="+replyId;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((json) => json.value ? this.createDialogueStepObject(json.value) : null);
    }

    continueDialogue(projectSlug, dialogueName) {
        var url = this._baseUrl + "/dialogue/continue";

        url += "?projectSlug="+encodeURIComponent(projectSlug);
        url += "&dialogueName="+dialogueName;
        url += "&timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => {
            var dialogueData = data?.value;
            if (dialogueData && 'dialogue' in dialogueData) {
                // Create a DialogueStep object from the received data
                return this.createDialogueStepObject(dialogueData);
            }
            return null;
        });
    }

    cancelDialogue(loggedDialogueId) {
        let url = this._baseUrl + "/dialogue/cancel?loggedDialogueId=" + loggedDialogueId;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    // -----------------------------------------------------------------
    // ---------- Draft dialogue test-execution (ephemeral) ----------
    // -----------------------------------------------------------------

    listDraftDialogues(projectSlug) {
        const url = this._baseUrl + "/authoring/list-dialogues?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    startDraftDialogue(projectSlug, dialogueName, language, startNodeId) {
        let url = this._baseUrl + "/draft/start";

        url += "?projectSlug=" + encodeURIComponent(projectSlug);
        url += "&dialogueName=" + encodeURIComponent(dialogueName);
        url += "&language=" + language;
        url += "&timeZone=" + this._timeZone;
        if (startNodeId) url += "&startNodeId=" + encodeURIComponent(startNodeId);

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((json) => ({
            draftSessionId: json.draftSessionId,
            dialogueStep: this.createDialogueStepObject(json.dialogueMessage),
        }));
    }

    progressDraftDialogue(draftSessionId, replyId) {
        let url = this._baseUrl + "/draft/progress";

        url += "?draftSessionId=" + draftSessionId;
        url += "&replyId=" + replyId;
        url += "&timeZone=" + this._timeZone;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((json) => json.value ? this.createDialogueStepObject(json.value) : null);
    }

    cancelDraftDialogue(draftSessionId) {
        const url = this._baseUrl + "/draft/cancel?draftSessionId=" + draftSessionId;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    revertDraftVariables(draftSessionId) {
        let url = this._baseUrl + "/draft/revert-variables?draftSessionId=" + draftSessionId;
        url += "&timeZone=" + this._timeZone;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    // -----------------------------------------------------------------
    // ---------- Authoring (draft dialogue & node CRUD) ----------
    // -----------------------------------------------------------------

    createDraftDialogue(projectSlug, name) {
        const url = this._baseUrl + "/authoring/create-dialogue?projectSlug=" + encodeURIComponent(projectSlug);

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ name }),
        }).then((response) => this._handleResponse(response));
    }

    // Soft-delete: marks the dialogue as pending deletion (reversible via restoreDraftDialogue)
    // until the project is next published.
    deleteDraftDialogue(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/delete-dialogue?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    restoreDraftDialogue(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/restore-dialogue?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    // Scans the whole project for [[...]] reply links that reference the given dialogue (any
    // node within it) — used both to preview a rename's blast radius and to warn about dangling
    // links before a delete.
    findDialogueReferences(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/find-dialogue-references?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
        }).then((response) => this._handleResponse(response));
    }

    renameDraftDialogue(projectSlug, dialogueName, newName, updateReferences) {
        const url = this._baseUrl + "/authoring/rename-dialogue?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&newName=" + encodeURIComponent(newName)
            + "&updateReferences=" + !!updateReferences;

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    listDraftNodes(projectSlug, dialogueName) {
        const url = this._baseUrl + "/authoring/list-nodes?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
        }).then((response) => this._handleResponse(response));
    }

    createDraftNode(projectSlug, dialogueName, title, header, body) {
        const url = this._baseUrl + "/authoring/create-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName);

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ title, header, body }),
        }).then((response) => this._handleResponse(response));
    }

    updateDraftNode(projectSlug, dialogueName, nodeTitle, header, body) {
        const url = this._baseUrl + "/authoring/update-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&nodeTitle=" + encodeURIComponent(nodeTitle);

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ header, body }),
        }).then((response) => this._handleResponse(response));
    }

    deleteDraftNode(projectSlug, dialogueName, nodeTitle) {
        const url = this._baseUrl + "/authoring/delete-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&nodeTitle=" + encodeURIComponent(nodeTitle);

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    // Scans the whole project for [[...]] reply links that reference the given node — used both
    // to preview a rename's blast radius and to warn about dangling links before a delete.
    findNodeReferences(projectSlug, dialogueName, nodeTitle) {
        const url = this._baseUrl + "/authoring/find-node-references?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&nodeTitle=" + encodeURIComponent(nodeTitle);

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            },
        }).then((response) => this._handleResponse(response));
    }

    renameDraftNode(projectSlug, dialogueName, oldTitle, newTitle, updateReferences) {
        const url = this._baseUrl + "/authoring/rename-node?projectSlug=" + encodeURIComponent(projectSlug)
            + "&dialogueName=" + encodeURIComponent(dialogueName)
            + "&oldTitle=" + encodeURIComponent(oldTitle)
            + "&newTitle=" + encodeURIComponent(newTitle)
            + "&updateReferences=" + !!updateReferences;

        return this._fetch(url, {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    getVariables() {
        var url = this._baseUrl + "/variables/get";

        url += "?timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => {
            if(data == null || data.length == 0) {
                return new Array();
            } else {
                var variables = new Array();

                data.forEach(entry => {
                    var variable = new Variable();
                    variable.name = entry.name;
                    variable.value = entry.value;
                    variable.updatedTime = entry.updatedTime;
                    variable.updatedTimeZone = entry.updatedTimeZone;
                    variable.updatedSource = entry.updatedSource;
                    variables.push(variable);
                });

                return variables;
            }
        })
    }

    getOngoingDialogue(projectSlug) {
        let url = this._baseUrl + "/dialogue/get-ongoing";
        url += "?projectSlug=" + encodeURIComponent(projectSlug);
        url += "&timeZone=" + this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => data?.value ?? null);
    }

    setVariable(variableName, variableValue) {
        var url = this._baseUrl + "/variables/set-single";

        url += "?name="+variableName;
        if(variableValue != null) url += "&value="+variableValue;
        url += "&timeZone="+this._timeZone;
        url += this._delegateParam;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    // ----------------------------------------------------------
    // ---------- Helper functions related to Dialogue ----------
    // ----------------------------------------------------------

    createDialogueStepObject(data) {
        // Instantiate an empty DialogueStep
        var dialogueStep = DialogueStep.emptyInstance();

        // Add the simple parameters
        dialogueStep.dialogueName = data.dialogue;
        dialogueStep.node = data.node;
        dialogueStep.speaker = data.speaker;
        dialogueStep.loggedDialogueId = data.loggedDialogueId;
        dialogueStep.loggedInteractionIndex = data.loggedInteractionIndex;

        // Add the statement (consisting of a list of segments)
        var statement = Statement.emptyInstance();
        data.statement.segments.forEach(
            (element) => {
                var segment = new Segment(element.segmentType,element.text);
                statement.addSegment(segment);
            }
        );
        dialogueStep.statement = statement;

        // Add the replies
        data.replies.forEach(
            (element) => {
                var reply = null;
                if(element.statement == null) {
                    reply = AutoForwardReply.emptyInstance();
                } else {
                    reply = BasicReply.emptyInstance();
                }
                reply.replyId = element.replyId;
                reply.endsDialogue = element.endsDialogue;
                
                if(reply instanceof BasicReply) {
                    statement = Statement.emptyInstance();
                    element.statement.segments.forEach(
                        (segmentElement) => {
                            var segment = new Segment(segmentElement.segmentType,segmentElement.text);
                            statement.addSegment(segment);
                        }
                    );
                    reply.statement = statement;
                }
                reply.actions = element.actions; // TODO: Unfold 'actions' into Action-objects
                dialogueStep.addReply(reply);
            }
        );
        return dialogueStep;
    }

    async _fetch(url, options, logRequestBody = null) {
        const path = url.startsWith(this._baseUrl) ? url.slice(this._baseUrl.length) : url;
        const method = options?.method || 'GET';
        const hasAuth = !!options?.headers?.['Authorization'];

        let response;
        try {
            response = await fetch(url, options);
        } catch (networkError) {
            logApiCall(method, path, 0, null, logRequestBody);
            throw networkError;
        }
        const text = await response.text().catch(() => null);
        logApiCall(method, path, response.status, text, logRequestBody);
        const nullBodyStatus = [204, 205, 304].includes(response.status);
        const reconstructed = new Response(nullBodyStatus ? null : text, {
            status: response.status,
            statusText: response.statusText,
            headers: response.headers,
        });

        if (response.status === 401 && hasAuth && this._onUnauthorized) {
            const newToken = await this._onUnauthorized().catch(() => null);
            if (newToken) {
                const retryOptions = {
                    ...options,
                    headers: { ...options.headers, 'Authorization': 'Bearer ' + newToken },
                };
                const retryResponse = await fetch(url, retryOptions);
                const retryText = await retryResponse.text().catch(() => null);
                logApiCall(method + ' [retried]', path, retryResponse.status, retryText, logRequestBody);
                const retryNullBodyStatus = [204, 205, 304].includes(retryResponse.status);
                return new Response(retryNullBodyStatus ? null : retryText, {
                    status: retryResponse.status,
                    statusText: retryResponse.statusText,
                    headers: retryResponse.headers,
                });
            }
        }

        return reconstructed;
    }

    _handleResponse(response) {
        if (response.ok) {
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.startsWith('application/json')) {
                // A 2xx response can still have an empty/malformed body (e.g. truncated by a
                // proxy) — without this catch, response.json()'s rejection has no `status` field
                // and describeError() would mislabel it as a generic network error.
                return response.json().catch(() => Promise.reject({
                    status: response.status,
                    statusText: response.statusText,
                    code: null,
                    message: 'The server returned an invalid response.',
                    fieldErrors: [],
                    errors: null,
                }));
            } else {
                return response.text();
            }
        }
        // Error responses are JSON HttpError bodies (code, message, fieldErrors, and — for some
        // errors, e.g. a project that fails to parse — a structured "errors" field mirroring
        // /publish/verify's shape). Parse it so callers (see error-message.js) can show the
        // actual backend message instead of just the HTTP status.
        return response.json()
            .catch(() => null)
            .then((body) => Promise.reject({
                status: response.status,
                statusText: response.statusText,
                code: body?.code ?? null,
                message: body?.message ?? null,
                fieldErrors: body?.fieldErrors ?? [],
                errors: body?.errors ?? null,
            }));
    }
}
