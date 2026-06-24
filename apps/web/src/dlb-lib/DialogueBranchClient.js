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

    constructor(baseUrl, accessToken) {
        this._baseUrl = baseUrl;
        this._accessToken = accessToken;
        this._timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    onUnauthorized(onUnauthorized) {
        this._onUnauthorized = onUnauthorized;
    }

    login(user, password) {
        const loginUrl = this._baseUrl + "/auth/login";

        return this._fetch(loginUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                user: user,
                password: password
            }),
        })
        .then((response) => {
            if (response.ok) {
                return response.json();
            } else {
                return Promise.reject(response);
            }
        });
    }

    logout() {
        return this._fetch(this._baseUrl + "/auth/logout", {
            method: "POST",
            headers: { 'Authorization': 'Bearer ' + this._accessToken },
        }).then((response) => this._handleResponse(response));
    }

    refresh(refreshToken) {
        return this._fetch(this._baseUrl + "/auth/refresh", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken }),
        })
        .then((response) => this._handleResponse(response));
    }

    getServerInfo() {
        return this._fetch(this._baseUrl + "/info/all", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        })
        .then((response) => this._handleResponse(response));
    }

    listDialogues() {
        const url = this._baseUrl + "/admin/list-dialogues";

        return this._fetch(url, {
            method: "GET",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    startDialogue(dialogueName, language) {
        var url = this._baseUrl + "/dialogue/start";

        url += "?dialogueName="+dialogueName;
        url += "&language="+language;
        url += "&timeZone="+this._timeZone;

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

    continueDialogue(dialogueName) {
        var url = this._baseUrl + "/dialogue/continue";

        url += "?dialogueName="+dialogueName;
        url += "&timeZone="+this._timeZone;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response))
        .then((data) => {
            if('value' in data) {
                var dialogueData = data.value;
                if('dialogue' in dialogueData) {
                    // Create a DialogueStep object from the received data
                    return this.createDialogueStepObject(dialogueData);
                }
            }
            return null;
        });
    }

    cancelDialogue(loggedDialogueId) {
        const url = this._baseUrl + "/dialogue/cancel?loggedDialogueId=" + loggedDialogueId;

        return this._fetch(url, {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + this._accessToken,
                "Content-Type": "application/json",
            }
        })
        .then((response) => this._handleResponse(response));
    }

    getVariables() {
        var url = this._baseUrl + "/variables/get";

        url += "?timeZone="+this._timeZone;

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
                    variables.push(variable);
                });

                return variables;
            }
        })
    }

    setVariable(variableName, variableValue) {
        var url = this._baseUrl + "/variables/set-single";

        url += "?name="+variableName;
        if(variableValue != null) url += "&value="+variableValue;
        url += "&timeZone="+this._timeZone;

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

    async _fetch(url, options) {
        const path = url.startsWith(this._baseUrl) ? url.slice(this._baseUrl.length) : url;
        const method = options?.method || 'GET';
        const hasAuth = !!options?.headers?.['Authorization'];

        const response = await fetch(url, options);
        const cloned = response.clone();
        const text = await cloned.text().catch(() => null);
        logApiCall(method, path, response.status, text);

        if (response.status === 401 && hasAuth && this._onUnauthorized) {
            const newToken = await this._onUnauthorized().catch(() => null);
            if (newToken) {
                const retryOptions = {
                    ...options,
                    headers: { ...options.headers, 'Authorization': 'Bearer ' + newToken },
                };
                const retryResponse = await fetch(url, retryOptions);
                const retryCloned = retryResponse.clone();
                const retryText = await retryCloned.text().catch(() => null);
                logApiCall(method + ' [retried]', path, retryResponse.status, retryText);
                return retryResponse;
            }
        }

        return response;
    }

    _handleResponse(response) {
        if (response.ok) {
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.startsWith('application/json')) {
                return response.json();
            } else {
                return response.text();
            }
        } else {
            return Promise.reject(response);
        }
    }
}
