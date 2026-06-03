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

import { User } from './model/User.js';
import { Segment } from './model/Segment.js';
import { Variable } from './model/Variable.js';
import { Statement } from './model/Statement.js';
import { ServerInfo } from './model/ServerInfo.js';
import { BasicReply } from './model/BasicReply.js';
import { DialogueStep } from './model/DialogueStep.js';
import { OngoingDialogue } from './model/OngoingDialogue.js';
import { AutoForwardReply } from './model/AutoForwardReply.js';

export class DialogueBranchClient {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor(baseUrl, logger, clientController) {
        this._LOGTAG = "DialogueBranchClient";
        this._baseUrl = baseUrl;
        this._logger = logger;
        this._clientController = clientController;
        // eslint-disable-next-line no-undef
        this._timeZone = new Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    get serverInfo() {
        return this._serverInfo;
    }

    get baseUrl() {
        return this._baseUrl;
    }

    get timeZone() {
        return this._timeZone;
    }

    get user() {
        return this._user;
    }

    set user(user) {
        this._user = user;
    }

    get logger() {
        return this._logger;
    }

    // -----------------------------------------------------------------------------
    // ---------- 1. Authentication: End-points related to Authentication ----------
    // -----------------------------------------------------------------------------

    // --------------------------------------------
    // ---------- End-Point: /auth/login ----------
    // --------------------------------------------

    /**
     * Performs a call to the /auth/login end-point. A successfull call will result in a call to the
     * this.loginSuccess() function, while an error will result in a call to this.loginError(). Both
     * of these functions will perform basic actions (see their individual documentation) and then
     * call the 'customLoginSuccess' and 'customLoginError' functions respectively that you may
     * define yourself to perform additional actions. 
     *
     * Note that "success" does not necessarily mean that the authentication was successful.
     * Providing an invalid username/password combination is deemed a "success", but will result in
     * an error message being delivered to loginSuccess().
     *
     * @param {String} user The username of the Dialogue Branch Web Service user.
     * @param {String} password The password corresponding to the user.
     * @param {Number} tokenExpiration The time (in minutes) after which the authentication token
     *                                 should expire. This can be set to '0' or 'never' if the token
     *                                 should never expire.
     */
    callLogin(user, password, tokenExpiration) {
        const loginUrl = this._baseUrl + "/auth/login";

        fetch(loginUrl, {
            method: "POST",
            headers: {
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            },
            body: JSON.stringify({
                user: user,
                password: password,
                tokenExpiration: tokenExpiration
            }),
        })
        .then((response) => {
            if(response.ok) {
                return response.json();
            }
            return Promise.reject(response);
        })
        .then((data) => {
            this._user = new User(data.user,data.role,data.token);
            this.logger.debug(this._LOGTAG,"Handling successful login attempt for user '" + this._user.name + 
                "' with role '" + this._user.role + "' and authToken '" + this._user.authToken + "'.");
            this._clientController.handleLoginSuccess(this._user);
        })
        .catch((response) => {
            if(response.status == 400 || response.status == 401) {
                response.json().then((data) => {
                    this.logger.debug(this._LOGTAG,
                        "Handling failed login attempt (HTTP Status: '" + response.status 
                        + "') with errorcode '" + data.code + "' and message '" + data.message + "', and fieldErrors: " 
                        + JSON.stringify(data.fieldErrors));
                    this._clientController.handleLoginError(response.status, data.code, data.message, data.fieldErrors);
                })
            } else {
                this.logger.debug(this._LOGTAG,
                    "Handling failed login attempt (HTTP Status: '" + response.status 
                    + "'). An unknown error has occured.");
                this._clientController.handleLoginError(response.status, "UNKNOWN_ERROR", "An unknown error has occured.",null);
            }
        });

    }

    // -----------------------------------------------
    // ---------- End-Point: /auth/validate ----------
    // -----------------------------------------------

    /**
     * Performs a call to the /auth/validate end-point. 
     */
    callAuthValidate(tokenToValidate) {
        var url = this._baseUrl + "/auth/validate";

        fetch(url, {
            method: "POST",
            headers: {
                'X-Auth-Token': tokenToValidate,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })

        .then((response) => {
            if(response.ok) {
                return response.json();
            }
            return Promise.reject(response);
        })
        .then((data) => {
            if(data == true) {
                this.logger.debug(this._LOGTAG,"AuthToken validated successfully.");
                this._clientController.handleAuthValidate(true, "Success.");
            } else {
                var errorMessage = "An unknown error occured while verifying the validity of the auth token.";
                this.logger.debug(this._LOGTAG,errorMessage);
                this._clientController.handleAuthValidate(false, errorMessage);
            }
        })
        .catch((response) => {
            var errorMessage = "Failed to validate Auth Token (HTTP Status: '" + response.status + "').";
            this.logger.debug(this._LOGTAG,errorMessage);
            this._clientController.handleAuthValidate(false,errorMessage);
        });
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // ---------- 2. Dialogue: End-points for starting and controlling the lifecycle of remotely executed dialogues. ----------
    // ------------------------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------
    // ---------- End-Point: /dialogue/get-ongoing ----------
    // ------------------------------------------------------

    callGetOngoingDialogue() {
        var url = this._baseUrl + "/dialogue/get-ongoing";

        url += "?timeZone="+this._timeZone;

        fetch(url, {
            method: "GET",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => {
            if('value' in data) {
                if(data.value == null) {
                    this._clientController.handleOngoingDialogue(null);
                } else {
                    var ongoingDialogue = new OngoingDialogue(data.value.dialogueName, data.value.secondsSinceLastEngagement);
                    this._clientController.handleOngoingDialogue(ongoingDialogue);
                }
            } else {
                this._clientController.handleOngoingDialogue(null);
            }
        })
    }

    // ------------------------------------------------
    // ---------- End-Point: /dialogue/start ----------
    // ------------------------------------------------

    callStartDialogue(dialogueName, language) {
        var url = this._baseUrl + "/dialogue/start";

        url += "?dialogueName="+dialogueName;
        url += "&language="+language;
        url += "&timeZone="+this._timeZone;

        fetch(url, {
            method: "POST",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => { 
            if('dialogue' in data) {
                // Create a DialogueStep object from the received data
                var dialogueStep = this.createDialogueStepObject(data);

                // Let the controller handle it
                this._clientController.handleStartDialogue(dialogueStep);
            } else {
                var errorMessage = "No data received when starting dialogue.";
                this.logger.error(this._LOGTAG,errorMessage);
                this._clientController.handleStartDialogueError(errorMessage);
            }
        })
        .catch((err) => {
            var errorMessage = "An unexpected error occured when starting a dialogue: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleStartDialogueError(errorMessage);
        });
    }

    // ---------------------------------------------------
    // ---------- End-Point: /dialogue/progress ----------
    // ---------------------------------------------------

    callProgressDialogue(loggedDialogueId, loggedInteractionIndex, replyId) {
        var url = this._baseUrl + "/dialogue/progress";

        url += "?loggedDialogueId="+loggedDialogueId;
        url += "&loggedInteractionIndex="+loggedInteractionIndex;
        url += "&replyId="+replyId;

        fetch(url, {
            method: "POST",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => { 
            if('value' in data) {

                // The response is empty, so the dialogue is over.
                if(data.value == null) {
                    this._clientController.handleProgressDialogue(false,null);
                
                // There is dialogue, so render the next step.
                } else if('dialogue' in data.value) {
                    var dialogueStep = this.createDialogueStepObject(data.value);
                    this._clientController.handleProgressDialogue(true, dialogueStep);
    
                // Else, something is wrong.
                } else {
                    var errorMessage = "The Web Service returned an unexpected response when progressing the dialogue.";
                    this.logger.error(this._LOGTAG,errorMessage);
                    this._clientController.handleProgressDialogueError(errorMessage);
                }
            }
        })
        .catch((err) => {
            var errorMessage = "The Web Service returned an unexpected response when progressing the dialogue: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleProgressDialogueError(errorMessage);
        });

    }

    // ---------------------------------------------------
    // ---------- End-Point: /dialogue/continue ----------
    // ---------------------------------------------------

    callContinueDialogue(dialogueName) {
        var url = this._baseUrl + "/dialogue/continue";

        url += "?dialogueName="+dialogueName;
        url += "&timeZone="+this._timeZone;

        fetch(url, {
            method: "POST",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => { 
            if('value' in data) {
                var dialogueData = data.value;
                if('dialogue' in dialogueData) {
                    // Create a DialogueStep object from the received data
                    var dialogueStep = this.createDialogueStepObject(dialogueData);

                    // Let the controller handle it
                    this._clientController.handleStartDialogue(dialogueStep);
                }
            } else {
                var errorMessage = "No data received when trying to continue dialogue.";
                this.logger.error(this._LOGTAG,errorMessage);
                this._clientController.handleStartDialogueError(errorMessage);
            }
        })
        .catch((err) => {
            var errorMessage = "An unexpected error occured when attempting to continue a dialogue: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleStartDialogueError(errorMessage);
        });
    }

    // -------------------------------------------------
    // ---------- End-Point: /dialogue/cancel ----------
    // -------------------------------------------------

    callCancelDialogue(loggedDialogueId) {
        var url = this._baseUrl + "/dialogue/cancel";

        url += "?loggedDialogueId="+loggedDialogueId;

        fetch(url, {
            method: "POST",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => {
            if(response.ok) {
                return;
            }
            return Promise.reject(response);
        })
        .then(() => { 
            this._clientController.handleCancelDialogue();
        })
        .catch((err) => {
            var errorMessage = "The Web Service returned an unexpected response when cancelling the dialogue: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleCancelDialogueError(errorMessage);
        });

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

    // -------------------------------------------------------------------------------------------------
    // ---------- 3. Variables: End-points for retrieving or setting DialogueBranch Variables. ----------
    // --------------------------------------------------------------------------------------------------

    // -----------------------------------------------
    // ---------- End-Point: /variables/get ----------
    // -----------------------------------------------

    callGetVariables() {
        var url = this._baseUrl + "/variables/get";

        url += "?timeZone="+this._timeZone;

        fetch(url, {
            method: "GET",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => { 
            if(data == null || data.length == 0) {
                this._clientController.handleGetVariables(new Array());
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

                this._clientController.handleGetVariables(variables);
            }
        })
        .catch((err) => {
            var errorMessage = "The Web Service returned an unexpected response when requesting variables list: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleGetVariablesError(errorMessage);
        });

    }

    // ------------------------------------------------------
    // ---------- End-Point: /variables/set-single ----------
    // ------------------------------------------------------

    callSetVariable(variableName, variableValue) {
        var url = this._baseUrl + "/variables/set-single";

        url += "?name="+variableName;
        if(variableValue != null) url += "&value="+variableValue;
        url += "&timeZone="+this._timeZone;

        fetch(url, {
            method: "POST",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => {
            if(response.ok) {
                return;
            }
            return Promise.reject(response);
        })
        .then(() => { 
            this._clientController.handleSetVariable(variableName);
        })
        .catch((err) => {
            var errorMessage = "The Web Service returned an unexpected response when updating a single variable: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleSetVariableError(variableName, errorMessage);
        });

    }

    // ---------------------------------------------------------------------------------------------------
    // ---------- 5. Information: End-points that provide information about the running service ----------
    // ---------------------------------------------------------------------------------------------------

    // --------------------------------------------
    // ---------- End-Point: /info/all ------------
    // --------------------------------------------

    /**
     * Performs a call to the /info/all end-point. A successfull call will result in a call to the
     * this._clientController.handleServerInfo() function, while an error will result in a call to
     * this._clientController.handleServerInfoError().
     *
     * The /info/all end-point returns information about the running web service, including:
     *  - Service Version - the software version number of the Web Service
     *  - Protocol Version - the latest protocol version supported (e.g. '1')
     *  - Build - the specific build-info string
     *  - Uptime - how long this web service has been running for
     *
     * @param {String} user The username of the Dialogue Branch Web Service user.
     * @param {String} password The password corresponding to the user.
     * @param {Number} tokenExpiration The time (in minutes) after which the authentication token
     *                                 should expire. This can be set to '0' or 'never' if the token
     *                                 should never expire.
     */
    callInfo() {
        var url = this._baseUrl + "/info/all";

        fetch(url, {
            method: "GET",
            headers: {
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => { 
            if('build' in data) {
                this._serverInfo = new ServerInfo(data.serviceVersion, data.protocolVersion, data.build, data.upTime);
                this._clientController.handleServerInfo(this._serverInfo);
            } else {
                var errorMessage = "Call to /info/all end-point resulted in an unexpected response.";
                this.logger.warn(this._LOGTAG,errorMessage);
                this._clientController.handleServerInfoError(errorMessage);
            }
        })
        .catch((err) => {
            var errorMessage = "Call to /info/all end-point resulted in an unexpected response: "+err;
            this.logger.warn(this._LOGTAG,errorMessage);
            this._clientController.handleServerInfoError(errorMessage);
        });
    }

    // --------------------------------------------------------------------------------------------------------
    // ---------- 6. Admin: End-points for administrative control of the Dialogue Branch Web Service. ----------
    // ---------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------
    // ---------- End-Point: /admin/list-dialogues ----------
    // ------------------------------------------------------

    callListDialogues() {
        var url = this._baseUrl + "/admin/list-dialogues";

        fetch(url, {
            method: "GET",
            headers: {
                'X-Auth-Token': this._user.authToken,
                Accept: "application/json, text/plain, */*",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning":"true",
                "User-Agent":"Dialogue Branch Web Client"
            }
        })
        .then((response) => response.json())
        .then((data) => { 
            if(data == null) {
                // A null response is unexpected, but should not break the client
                this.logger.warn(this._LOGTAG,"Call to /admin/list-dialogues returned empty response.");
                this._clientController.handleListDialogues(new Array());
            } else {
                if('dialogueNames' in data) {
                    this._clientController.handleListDialogues(data.dialogueNames);
                } else {
                    // Data without dialogueNames is unexpected, but should not break the client
                    this.logger.warn(this._LOGTAG,"Call to /admin/list-dialogues returned unexpected response.");
                    this._clientController.handleListDialogues(new Array());
                }
            }
        })
        .catch((err) => {
            var errorMessage = "Call to /admin/list-dialogues failed with the following error: "+err;
            this.logger.error(this._LOGTAG,errorMessage);
            this._clientController.handleListDialoguesError(err);
        });
    }

}
