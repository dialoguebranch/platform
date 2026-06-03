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

import { WCTAClientState } from './WCTAClientState.js';
import { TextAreaLogger } from '../dlb-lib/util/TextAreaLogger.js';
import { LOG_LEVEL_NAMES } from '../dlb-lib/util/AbstractLogger.js';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';
import { AbstractController } from '../dlb-lib/AbstractController.js';
import { DialogueBranchConfig } from '../dlb-lib/DialogueBranchConfig.js';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';
import { WCTATextRenderer } from './WCTATextRenderer.js';
import { WCTABalloonsRenderer } from './WCTABalloonsRenderer.js';

import { INTERACTION_TESTER_STYLE_TEXT } from './WCTAClientState.js';
import { INTERACTION_TESTER_STYLE_BALLOONS } from './WCTAClientState.js';

/**
 * The WCTAController (or Web Client Test Application Controller) is an implementation of an AbstractController
 * specifically for the Dialogue Branch Web Client Test Application. This class implementats all the required handler
 * functions that are called by the DialogueBranchClient, and handles all the updates to the application specific user
 * interface.
 * 
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class WCTAController extends AbstractController {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor() {
        super();

        this._LOGTAG = "WCTAController";

        this._dialogueReplyElements = new Array();
        this._dialogueReplyNumbers = new Array();
    }

    async initialize() {
        // First initialize the configuration in an asynchronous way
        await this.initializeConfig();
        
        this._logger = new TextAreaLogger(this._dialogueBranchConfig.logLevel, document.getElementById("debug-textarea"));
        this.logger.info(this._LOGTAG,"Initialized Logger with log level '" 
            + this._dialogueBranchConfig.logLevel 
            + "' ('" 
            + LOG_LEVEL_NAMES[this._dialogueBranchConfig.logLevel] 
            + "').");

        // Initialize the DialogueBranchClient object, used for communication to the Dialogue Branch Web Service
        this._dialogueBranchClient = new DialogueBranchClient(this._dialogueBranchConfig.baseUrl, this.logger, this);
        this.logger.info(this._LOGTAG,"Initalized DialogueBranchClient directed to the Web Service at '"+ this._dialogueBranchConfig.baseUrl + "'.");

        // Initialize the ClientState object and take actions
        this._clientState = new WCTAClientState(this.logger);
        this._clientState.loadFromCookie();

        // If user info was loaded from Cookie, validate the authToken that was found
        if(this._clientState.user != null) {
            this.logger.info(this._LOGTAG, "Existing user info found in cookie - username: " + this._clientState.user.name + ", role: " + this._clientState.user.role);
            this._dialogueBranchClient.user = this._clientState.user;
            this._dialogueBranchClient.callAuthValidate(this._clientState.user.authToken);
        }
        
        this._interactionTextRenderer = new WCTATextRenderer(this);
        this._interactionBalloonsRenderer = new WCTABalloonsRenderer(this);

        // Make a call to the Web Service for service info.
        this._dialogueBranchClient.callInfo();

        // Update the UI State
        this.updateUIState();

        // Update the size of the interaction tester / variable/dialogue browsers
        this.updateInteractionTesterSize();
    }

    async initializeConfig() {
        this._dialogueBranchConfig = new DialogueBranchConfig();
        await this._dialogueBranchConfig.loadFromFile();
    }

    // -----------------------------------------------------------
    // -------------------- Getters & Setters --------------------
    // -----------------------------------------------------------

    set dialogueReplyElements(dialogueReplyElements) {
        this._dialogueReplyElements = dialogueReplyElements;
    }
    
    get dialogueReplyElements() {
        return this._dialogueReplyElements;
    }

    set dialogueReplyNumbers(dialogueReplyNumbers) {
        this._dialogueReplyNumbers = dialogueReplyNumbers;
    }

    get dialogueReplyNumbers() {
        return this._dialogueReplyNumbers;
    }

    get logger() {
        return this._logger;
    }

    get logTag() {
        return this._LOGTAG;
    }

    get interactionTextRenderer() {
        return this._interactionTextRenderer;
    }

    get interactionBalloonsRenderer() {
        return this._interactionBalloonsRenderer;
    }

    // ------------------------------------------------------
    // -------------------- User Actions --------------------
    // ------------------------------------------------------

    // ---------- Login ----------

    actionLogin(event) {
        event.preventDefault();

        // Remove any possible previous error indications
        document.getElementById("login-form-password-field").classList.remove("login-form-error-class");
        document.getElementById("login-form-username-field").classList.remove("login-form-error-class");

        var formUsername = document.getElementById("login-form-username-field").value;
        var formPassword = document.getElementById("login-form-password-field").value;

        this._dialogueBranchClient.callLogin(formUsername, formPassword, 0);
    }

    // ---------- Logout ----------

    actionLogout(event) {
        event.preventDefault();

        this.logger.info(this._LOGTAG,"Logging out user '" + this._clientState.user.name + "'.");
        DocumentFunctions.deleteCookie('user.name');
        DocumentFunctions.deleteCookie('user.authToken');
        DocumentFunctions.deleteCookie('user.role');

        this._clientState.user = null;
        this._clientState.loggedIn = false;
        this._dialogueBranchClient.user = null;
        this.updateUIState();
    }

    // ---------- Toggle Debug Console ----------

    /**
     * Toggle the visibility of the Debug Console. If it was visible before, make it invisible, and the other way
     * around. Store the new state in the ClientState.
     */
    actionToggleDebugConsole(event) {
        event.preventDefault();

        if(this._clientState.debugConsoleVisible) {
            this.setDebugConsoleVisibility(false);
            this._clientState.debugConsoleVisible = false;
        } else {
            this.setDebugConsoleVisibility(true);
            this._clientState.debugConsoleVisible = true;
        }
    }

    // ------------------------------------------------------------
    // -------------------- Interaction Tester --------------------
    // ------------------------------------------------------------

    actionCancelDialogue(loggedDialogueId) {
        this.logger.info(this._LOGTAG,"Cancelling the current dialogue with loggedDialogueId: '"+loggedDialogueId+"'.");
        this._dialogueBranchClient.callCancelDialogue(loggedDialogueId);
    }

    handleCancelDialogue() {
        this.renderDialogueStep(null, "Dialogue Cancelled");
    }

    handleCancelDialogueError(errorMessage) {
        this.logger.error(this._LOGTAG,"Cancelling dialogue failed with the following result: "+errorMessage);
    }

    // ----------------------------------------------------------
    // -------------------- Dialogue Browser --------------------
    // ----------------------------------------------------------

    actionRefreshDialogueBrowser(event) {
        if(event != null) event.preventDefault();
        this._dialogueBranchClient.callListDialogues();
    }

    actionResizeDialogueBrowser() {

        if(this._clientState.dialogueBrowserExtended) {
            this._clientState.dialogueBrowserExtended = false;
        } else {
            this._clientState.dialogueBrowserExtended = true;
        }
        
        this.updateInteractionTesterSize();
    }

    handleListDialogues(dialogueNames) {
        var dialogueBrowserContentField = document.getElementById("dialogue-browser-content");

        // Empty the content field before (re-)populating
        dialogueBrowserContentField.innerHTML = "";

        // If the list of dialogueNames is empty, present a warning message
        if(dialogueNames.length == 0) {
            var dialogueBrowserEmptyElement = document.createElement("div");
            dialogueBrowserEmptyElement.classList.add("dialogue-browser-empty-warning");
            dialogueBrowserEmptyElement.innerHTML = "No dialogues available. Please make sure that the Dialogue Branch Web Service you are connected to has access to dialogue script resources.";
            dialogueBrowserContentField.appendChild(dialogueBrowserEmptyElement);
        } else {
            for(var i=0; i< dialogueNames.length; i++) {

                var dialogueBrowserEntry = document.createElement("div");
                dialogueBrowserEntry.classList.add("dialogue-browser-entry");
                dialogueBrowserEntry.innerHTML = "<i class='fa-solid fa-circle-play'></i> " + dialogueNames[i];

                dialogueBrowserEntry.addEventListener("click", this.actionStartDialogue.bind(this, dialogueNames[i]), false);
                dialogueBrowserContentField.appendChild(dialogueBrowserEntry);
            }
        }

        this.logger.info(this._LOGTAG,"Updated the contents of the Dialogue Browser, showing "+dialogueNames.length+" available dialogues.");
    }

    handleListDialoguesError(errorMessage) {
        this.logger.error(this._LOGTAG,errorMessage);
    }

    // ----------------------------------------------------------
    // -------------------- Variable Browser --------------------
    // ----------------------------------------------------------

    actionRefreshVariableBrowser() {
        this._dialogueBranchClient.callGetVariables();
    }

    actionResizeVariableBrowser() {
        if(this._clientState.variableBrowserExtended) {
            this._clientState.variableBrowserExtended = false;
        } else {
            this._clientState.variableBrowserExtended = true;
        }
        this.updateInteractionTesterSize();
    }

    handleGetVariables(variables) {
        var variableBrowserContentField = document.getElementById("variable-browser-content");

        // Empty the content field before populating
        variableBrowserContentField.innerHTML = "";

        // Add some headers to the Variables 'table'
        var headerElement = document.createElement("div");
        headerElement.classList.add("variable-browser-entry");
        headerElement.classList.add("variable-browser-header");
        variableBrowserContentField.appendChild(headerElement);

        var variableButtonsLabel = document.createElement("div");
        variableButtonsLabel.classList.add("variable-buttons-box");
        variableButtonsLabel.innerHTML = "Actions";
        headerElement.appendChild(variableButtonsLabel);

        var variableNameLabel = document.createElement("div");
        variableNameLabel.classList.add("variable-entry-name");
        variableNameLabel.innerHTML = "Name";
        headerElement.appendChild(variableNameLabel);

        var variableValueLabel = document.createElement("div");
        variableValueLabel.classList.add("variable-entry-value");
        variableValueLabel.innerHTML = "Value";
        headerElement.appendChild(variableValueLabel);

        var variableUpdatedLabel = document.createElement("div");
        variableUpdatedLabel.classList.add("variable-entry-value");
        variableUpdatedLabel.innerHTML = "Last Updated";
        headerElement.appendChild(variableUpdatedLabel);

        variables.forEach(variable => {
            var variableEntryElement = document.createElement("div");
            variableEntryElement.classList.add("variable-browser-entry");
            variableBrowserContentField.appendChild(variableEntryElement);

            var variableButtonsBox = document.createElement("div");
            variableButtonsBox.classList.add("variable-buttons-box");
            variableEntryElement.appendChild(variableButtonsBox);
            
            var variableDeleteIcon = document.createElement("button");
            variableDeleteIcon.classList.add("variable-delete-icon");
            variableDeleteIcon.innerHTML = "<i class='fa-solid fa-trash'></i>";
            variableDeleteIcon.addEventListener("click", this.actionDeleteVariable.bind(this, variable.name), false);
            variableButtonsBox.appendChild(variableDeleteIcon);

            var variableNameElement = document.createElement("div");
            variableNameElement.classList.add("variable-entry-name");
            variableNameElement.innerHTML = variable.name;
            variableEntryElement.appendChild(variableNameElement);

            var variableValueElement = document.createElement("div");
            variableValueElement.classList.add("variable-entry-value");
            variableValueElement.innerHTML = variable.value;
            variableEntryElement.appendChild(variableValueElement);

            var variableUpdatedElement = document.createElement("div");
            variableUpdatedElement.classList.add("variable-entry-updated");
            variableUpdatedElement.innerHTML = variable.getReadableTimeSinceLastUpdate();
            variableEntryElement.appendChild(variableUpdatedElement);
        });

        this.logger.info(this._LOGTAG,"Updated the contents of the Variable Browser, showing "+variables.length+" available variables.");

    }

    handleGetVariablesError(errorMessage) {
        this.logger.error(this._LOGTAG,errorMessage);
    }

    actionDeleteVariable(variableName) {
        this.logger.info(this._LOGTAG,"Requesting to delete variable: '" + variableName + "'.");
        this._dialogueBranchClient.callSetVariable(variableName,null);
    }

    handleSetVariable(variableName) {
        this.logger.info(this._LOGTAG,"Succesfully updated variable '" + variableName + "'.");
        this.actionRefreshVariableBrowser();
    }

    handleSetVariableError(variableName, errorMessage) {
        this.logger.error(this._LOGTAG,"Error updating variable '"+variableName+"', with the following message: " + errorMessage);
    }

    // ---------- Start Dialogue ----------

    actionStartDialogue(dialogueName) {
        this._logger.info(this._LOGTAG, "Starting dialogue '" + dialogueName + "'.");

        // Todo: call the renderer's and tell them to 'clear'
        this.interactionTextRenderer.clear();
        this.interactionBalloonsRenderer.clear();
        
        this._dialogueBranchClient.callStartDialogue(dialogueName,"en");
    }

    actionSelectReply(replyNumber, reply, dialogueStep) {
        // Add a class to the selected reply option, so it can be visualised in the dialogue history which options were chosen
        this._dialogueReplyElements[replyNumber-1].classList.add("user-selected-reply-option");
        this._dialogueReplyNumbers[replyNumber-1].classList.add("user-selected-reply-option");
        this._dialogueBranchClient.callProgressDialogue(dialogueStep.loggedDialogueId, dialogueStep.loggedInteractionIndex, reply.replyId);
    }

    // ----------------------------------------------------------------------
    // -------------------- Handling DLB Client Response --------------------
    // ----------------------------------------------------------------------

    // ---------- Info ----------

    handleServerInfo(serverInfo) {
        this.logger.info(this._LOGTAG,"Connected to Dialogue Branch Web Service v"+serverInfo.serviceVersion+", using protocol version "+serverInfo.protocolVersion+" (build: '"+serverInfo.build+"' running for "+serverInfo.upTime+").");
        this.updateServerInfoBox();
    }

    handleServerInfoError(errorMessage) {
        this.logger.error(this._LOGTAG,errorMessage);
    }

    // ---------- Login ----------

    handleLoginSuccess(user) {
        var formRemember = document.getElementById("login-form-remember-box").checked;
        this._clientState.user = user;
        this._clientState.loggedIn = true;
            
        if(formRemember) {
            DocumentFunctions.setCookie('user.name',this._clientState.user.name,365);
            DocumentFunctions.setCookie('user.authToken',this._clientState.user.authToken,365);
            DocumentFunctions.setCookie('user.role',this._clientState.user.role,365);

            this.logger.debug(this._LOGTAG,
                "Stored user info in cookie: user.name '" + 
                DocumentFunctions.getCookie('user.name') + 
                "', user.role '" + 
                DocumentFunctions.getCookie('user.role') + 
                "', user.authToken '" + 
                DocumentFunctions.getCookie('user.authToken') + 
                "'."
            );
        }

        // Clear the login error message box
        var errorMessageBox = document.getElementById("login-error-message-box");
        errorMessageBox.innerHTML = '';
        errorMessageBox.style.display = 'none';

        this.logger.info(this._LOGTAG,"User '"+this._clientState.user.name+"' successfully logged in with role '"+this._clientState.user.role+"'.");
        
        this.postLoginActions();
        this.updateServerInfoBox();
        this.updateUIState();
    }

    handleLoginError(httpStatusCode, errorCode, errorMessage, fieldErrors) {
        
        // Show the error message box
        var errorMessageBox = document.getElementById("login-error-message-box");
        errorMessageBox.style.display = 'block';
        errorMessageBox.innerHTML = errorMessage;
        
        if(errorCode == "INVALID_CREDENTIALS") {
            document.getElementById("login-form-password-field").classList.add("login-form-error-class");
            document.getElementById("login-form-username-field").classList.add("login-form-error-class");
        } else if (errorCode == "INVALID_INPUT") {
            for(let i=0; i<fieldErrors.length; i++) {
                if(fieldErrors[i].field == "user") document.getElementById("login-form-username-field").classList.add("login-form-error-class");
                if(fieldErrors[i].field == "password") document.getElementById("login-form-password-field").classList.add("login-form-error-class");
            }
        }
    }

    // ---------- Validate Authentication ----------

    handleAuthValidate(valid, message) {
        
        // All is well, mark user as logged in and proceed
        if(valid == true) {
            this._clientState.loggedIn = true;
            this.postLoginActions();
        
        // There is an invalid authToken in cookie, delete all info and assume user is logged out
        } else {
            this.logger.warn(this._LOGTAG,"Unable to validate stored authentication token: '" + message + "'. Requiring new login.");
            this._clientState.loggedIn = false;
            this._clientState.user = null;
            this._dialogueBranchClient.user = null;
            DocumentFunctions.deleteCookie('user.name');
            DocumentFunctions.deleteCookie('user.authToken');
            DocumentFunctions.deleteCookie('user.role');
        }

        // Finally, update the User Interface State
        this.updateUIState();
    }

    /**
     * Perform actions that need to happen immediately after a user has logged in.
     */
    postLoginActions() {
        this.logger.info(this._LOGTAG,"Performing post-login actions.");
        this._dialogueBranchClient.callGetOngoingDialogue();
    }

    // ------------------------------------------------------
    // ---------- End-Point: /dialogue/get-ongoing ----------
    // ------------------------------------------------------

    handleOngoingDialogue(ongoingDialogue) {
        // If called with null, there is no ongoing dialogue, and we are done here
        if(ongoingDialogue != null) {
            this.logger.info(this._LOGTAG,"Found an ongoing dialogue for user '"+this._clientState.user.name+"', dialogue name: '"+ongoingDialogue.name+"', with last engagement: "+ongoingDialogue.secondsSinceLastEngagement+" seconds ago.");
            
            // If the ongoing dialogue is less than a day old, pick up the conversation
            if(ongoingDialogue.secondsSinceLastEngagement <= 24 * 60 * 60) {
                this.logger.info(this._LOGTAG,"Ongoing dialogue is less than a day old, so picking up the conversation.");
                this._dialogueBranchClient.callContinueDialogue(ongoingDialogue.name);
            } else {
                this.logger.info(this._LOGTAG,"Ongoing dialogue is more than a day old, time to let go...");
            }
        }
        
    }
    
    // ---------- Start Dialogue

    handleStartDialogue(dialogueStep) {
        // Add the name of the newly started dialogue to the "Interaction Tester" title field
        var titleElement = document.getElementById("interaction-tester-title");
        titleElement.innerHTML = "Interaction Tester <i>(" + dialogueStep.dialogueName + ".dlb)</i>";

        // Enable the "cancel dialogue" button
        var cancelButton = document.getElementById("button-cancel-dialogue");
        cancelButton.addEventListener("click", this.actionCancelDialogue.bind(this, dialogueStep.loggedDialogueId), false);
        cancelButton.setAttribute('title',"Cancel the current ongoing dialogue.");
        cancelButton.classList.remove("button-disabled");

        // Render the newly created DialogueStep in the UI
        this.renderDialogueStep(dialogueStep);
    }

    handleStartDialogueError(errorMessage) {
        this.logger.error(this._LOGTAG,"Starting dialogue failed with the following result: "+errorMessage);
    }

    // ---------- Progress Dialogue

    handleProgressDialogue(dialogueContinues, dialogueStep) {
        if(dialogueContinues) {
            this.renderDialogueStep(dialogueStep);
        } else {
            this.renderDialogueStep(null);
        }
    }

    handleProgressDialogueError(errorMessage) {
        this.logger.error(this._LOGTAG,errorMessage);
    }

    // -----------------------------------------------------------------
    // -------------------- User Interface Handling --------------------
    // -----------------------------------------------------------------

    /**
     * Updates the text displayed in the "server info box", which may changed when a new call to the
     * service info end-point is done, or when a new user is logged in.
     */
    updateServerInfoBox() {

        var versionInfoBox = document.getElementById("version-info");
        var versionInfoString = "Not connected.";

        if(this._dialogueBranchClient.serverInfo != null) {
            var serverInfo = this._dialogueBranchClient.serverInfo;
            versionInfoString = "Connected to Dialogue Branch Web Service v" + serverInfo.serviceVersion;
            if(this._clientState.loggedIn) {
                versionInfoString += " as user '" + this._clientState.user.name +"'";
                if(this._clientState.user.role == 'admin') {
                    versionInfoString += " (with admin rights).";
                } else {
                    versionInfoString += ".";
                }
            }
        }
        versionInfoBox.innerHTML = versionInfoString;
    }

    updateUIState() {

        this.setDebugConsoleVisibility(this._clientState.debugConsoleVisible);

        this.setInteractionTesterStyle(this._clientState.interactionTesterStyle);

        if(this._clientState.loggedIn) {
            document.getElementById("navbar").style.display = 'block';
            document.getElementById("dialogue-container").style.display = 'block';
            document.getElementById("login-form").style.display = 'none';
            document.getElementById("dlb-splash-logo").style.display = 'none';
            document.getElementById("dlb-splash-text").style.display = 'none';
            var dialogueListButton = document.getElementById("button-refresh-dialogue-list");
            if(this._clientState.user.role == "admin") {
                dialogueListButton.classList.remove("button-disabled");
                dialogueListButton.setAttribute('title',"Refresh the content of the Dialogue Browser.");
            } else {
                dialogueListButton.classList.add("button-disabled");
                dialogueListButton.setAttribute('title',"Retrieving a dialogue list is only available for 'admin' users.");
            }

            // Refresh the Dialogue List
            if(this._clientState.user.role == 'admin')
            this.actionRefreshDialogueBrowser(null);

            // Refresh the Variable Browser
            this.actionRefreshVariableBrowser();

        } else {
            document.getElementById("navbar").style.display = 'none';
            document.getElementById("dialogue-container").style.display = 'none';
            document.getElementById("dialogue-browser-content").innerHTML = "";
            document.getElementById("interaction-tester-content-text").innerHTML = "";
            document.getElementById("interaction-tester-content-balloons").innerHTML = "";
            document.getElementById("variable-browser-content").innerHTML = "";
            document.getElementById("dlb-splash-logo").style.display = 'block';
            document.getElementById("dlb-splash-text").style.display = 'block';
            document.getElementById("login-form").style.display = 'block';
        }
    }

    updateInteractionTesterSize() {

        //<button id="button-resize-variable-list" class="circle-button-small" title="Resize the contents of the Variable Browser."><i class="fa-solid fa-caret-left"></i></button>
        var resizeDialogueBrowserButton = document.getElementById("button-resize-dialogue-list");
        var resizeVariableBrowserButton = document.getElementById("button-resize-variable-list");

        var dialogueBrowserContainer = document.getElementById("dialogue-browser-container");
        var variableBrowserContainer = document.getElementById("variable-browser-container");
        var interactionTesterContainer = document.getElementById("interaction-tester-container");


        var sideBarsExtended = 0;

        if(this._clientState.dialogueBrowserExtended) {
            sideBarsExtended++;
            resizeDialogueBrowserButton.innerHTML = "<i class=\"fa-solid fa-caret-left\"></i>";
            dialogueBrowserContainer.classList.remove("browser-size-default");
            dialogueBrowserContainer.classList.add("browser-size-extended");

        } else {
            resizeDialogueBrowserButton.innerHTML = "<i class=\"fa-solid fa-caret-right\"></i>";
            dialogueBrowserContainer.classList.remove("browser-size-extended");
            dialogueBrowserContainer.classList.add("browser-size-default");
        }

        if(this._clientState.variableBrowserExtended) {
            sideBarsExtended++;
            resizeVariableBrowserButton.innerHTML = "<i class=\"fa-solid fa-caret-right\"></i>";
            variableBrowserContainer.classList.remove("browser-size-default");
            variableBrowserContainer.classList.add("browser-size-extended");
        } else {
            resizeVariableBrowserButton.innerHTML = "<i class=\"fa-solid fa-caret-left\"></i>";
            variableBrowserContainer.classList.remove("browser-size-extended");
            variableBrowserContainer.classList.add("browser-size-default");
        }

        if(sideBarsExtended == 0) {
            interactionTesterContainer.classList.remove("tester-size-small");
            interactionTesterContainer.classList.remove("tester-size-smaller");
            interactionTesterContainer.classList.add("tester-size-default");
        } else if(sideBarsExtended == 1) {
            interactionTesterContainer.classList.remove("tester-size-smaller");
            interactionTesterContainer.classList.remove("tester-size-default");
            interactionTesterContainer.classList.add("tester-size-small");
        } else {
            interactionTesterContainer.classList.remove("tester-size-small");
            interactionTesterContainer.classList.remove("tester-size-default");
            interactionTesterContainer.classList.add("tester-size-smaller");
        }
    }

    // ---------- Debug Console ----------

    /**
     * Sets the visibility of the Debug Console based on the given parameter 'visible'. If true, the Debug Console
     * will be made visible, and vice versa.
     * @param {boolean} visible 
     */
    setDebugConsoleVisibility(visible) {
        if(visible) {
            document.getElementById("debug-console").style.display = 'inline';
            document.getElementById("toggle-debug-console").style.bottom = '220px';
            document.getElementById("version-info").style.bottom = '220px';
        } else {
            document.getElementById("debug-console").style.display = 'none';
            document.getElementById("toggle-debug-console").style.bottom = '10px';
            document.getElementById("version-info").style.bottom = '10px';
        }
    }

    // ---------- Interaction Tester Style ----------

    setInteractionTesterStyle(style) {

        if(style != INTERACTION_TESTER_STYLE_BALLOONS && style != INTERACTION_TESTER_STYLE_TEXT) {
            // Something is wrong, assume the 'text' style.
            style = INTERACTION_TESTER_STYLE_TEXT;
        }

        var interactionTesterStyleBalloonsButton = document.getElementById("button-interaction-style-balloons");
        var interactionTesterStyleTextButton = document.getElementById("button-interaction-style-text");

        if(style == INTERACTION_TESTER_STYLE_TEXT) {
            
            // Enable the "other" style buttons
            interactionTesterStyleBalloonsButton.addEventListener("click", this.setInteractionTesterStyle.bind(this, INTERACTION_TESTER_STYLE_BALLOONS), false);
            interactionTesterStyleBalloonsButton.setAttribute('title',"Switch to a balloon-based interaction style.");
            interactionTesterStyleBalloonsButton.classList.remove("button-disabled");

            // Disable the "text" style button
            interactionTesterStyleTextButton.classList.add("button-disabled");
            interactionTesterStyleTextButton.setAttribute('title',"Interaction style already in use.");
            interactionTesterStyleTextButton.replaceWith(interactionTesterStyleTextButton.cloneNode(true));

            document.getElementById("interaction-tester-content-text").style.visibility = 'visible';
            document.getElementById("interaction-tester-content-balloons").style.visibility = 'hidden';
            this.interactionBalloonsRenderer.hide();


        } else {
            // Enable the "other" style buttons
            interactionTesterStyleTextButton.addEventListener("click", this.setInteractionTesterStyle.bind(this, INTERACTION_TESTER_STYLE_TEXT), false);
            interactionTesterStyleTextButton.setAttribute('title',"Switch to a text-based interaction style.");
            interactionTesterStyleTextButton.classList.remove("button-disabled");

            // Disable the "balloon" style button
            interactionTesterStyleBalloonsButton.classList.add("button-disabled");
            interactionTesterStyleBalloonsButton.setAttribute('title',"Interaction style already in use.");
            interactionTesterStyleBalloonsButton.replaceWith(interactionTesterStyleBalloonsButton.cloneNode(true));

            document.getElementById("interaction-tester-content-balloons").style.visibility = 'visible';
            document.getElementById("interaction-tester-content-text").style.visibility = 'hidden';
            this.interactionBalloonsRenderer.unhide();
        }

        // Update the client state
        this._clientState.interactionTesterStyle = style;

    }

    // ---------- Dialogue Step Rendering ----------

    /**
     * Render a step in the dialogue given the information provided in the given dialogueStep object, or
     * render a "Dialogue Finished" statement if the given dialogueStep is null.
     *
     * @param {DialogueStep} dialogueStep the {@link DialogueStep} object to render, or null
     * @param {String} nullMessage the message explaining why dialogueStep is null.
     */
    renderDialogueStep(dialogueStep, nullMessage) {

        if(dialogueStep == null) {
            // Disable the "cancel dialogue" button
            var cancelButton = document.getElementById("button-cancel-dialogue");
            cancelButton.classList.add("button-disabled");
            cancelButton.setAttribute('title',"You can cancel a dialogue when there is a dialogue in progress.");
            cancelButton.replaceWith(cancelButton.cloneNode(true));
        }
        
        this.interactionTextRenderer.renderDialogueStep(dialogueStep, nullMessage);
        this.interactionBalloonsRenderer.renderDialogueStep(dialogueStep, nullMessage);
        
        // Refresh the Variable Browser
        this.actionRefreshVariableBrowser();

    }

}
