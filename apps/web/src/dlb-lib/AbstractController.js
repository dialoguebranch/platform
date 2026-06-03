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

/* eslint-disable no-unused-vars */

/**
 * An AbstractController is an "abstract" class that contains all the methods that should be
 * overridden by a a real implementation of a "Dialogue Branch User Interface Controller". A
 * DialogueBranchClient, which is used for all the communication to a Dialogue Branch Web Service
 * API is instantiated by passing an instance of an implementation of this "abstract class". Its
 * methods will be called as a result of API calls to the Web Service.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class AbstractController {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * This constructor should not be used, as AbstractController is an "abstract" class.
     */
    constructor() {
        if (this.constructor == AbstractController) {
            throw new Error("Abstract class AbstractController can not be instantiated.");
        }
    }

    // --------------------------------------
    // ---------- Abstract Methods ----------
    // --------------------------------------

    // -----------------------------------------------------------------------------
    // ---------- 1. Authentication: End-points related to Authentication ----------
    // -----------------------------------------------------------------------------

    // --------------------------------------------
    // ---------- End-Point: /auth/login ----------
    // --------------------------------------------

    /**
     * Called after a successful call to the /auth/login end-point by the DialogueBranchClient.
     * @param {User} user a User object containing the information of the logged in user.
     */
    handleLoginSuccess(user) {
        throw new Error("Method 'handleLoginSuccess()' must be implemented by a subclass.");
    }
    
    /**
     * Called after a failed call to the /auth/login end-point by the DialogueBranchClient.
     * @param {*} httpStatusCode the HTTP Status Code (e.g. '400').
     * @param {*} errorCode the error code as provided by the Dialogue Branch Web Service (e.g.
     * 'INVALID_CREDENTIALS').
     * @param {*} errorMessage the (human-readable) error message provided by the Web Service.
     * @param {*} fieldErrors an optional list of 'field errors' identifying which login fields were
     * problematic.
     */
    handleLoginError(httpStatusCode, errorCode, errorMessage, fieldErrors) {
        throw new Error("Method 'handleLoginError()' must be implemented by a subclass.");
    }

    // -----------------------------------------------
    // ---------- End-Point: /auth/validate ----------
    // -----------------------------------------------

    /**
     * Called after a successful call to the /auth/validate end-point. Indicates that the provided
     * auth token is indeed valid.
     * @param {Boolean} valid true if the token was validated, false otherwise.
     * @param {String} message a human-readable message indicating the success/failure.
     */
    handleAuthValidate(valid, message) {
        throw new Error("Method 'handleAuthValidate()' must be implemented by a subclass.");
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // ---------- 2. Dialogue: End-points for starting and controlling the lifecycle of remotely executed dialogues. ----------
    // ------------------------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------
    // ---------- End-Point: /dialogue/get-ongoing ----------
    // ------------------------------------------------------

    handleOngoingDialogue(ongoingDialogue) {
        throw new Error("Method 'handleOngoingDialogue()' must be implemented by a subclass.");
    }

    // ------------------------------------------------
    // ---------- End-Point: /dialogue/start ----------
    // ------------------------------------------------

    /**
     * Called after a successful call to the /dialogue/start end-point.
     * @param {DialogueStep} dialogueStep - the new dialogue step to be rendered.
     */
    handleStartDialogue(dialogueStep) {
        throw new Error("Method 'handleStartDialogue()' must be implemented by a subclass.");
    }
    
    /**
     * Called after a failed call to the /dialogue/start end-point.
     * @param {String} errorMessage a human-readable error message indicating the cause of the error.
     */
    handleStartDialogueError(errorMessage) { 
        throw new Error("Method 'handleStartDialogueError()' must be implemented by a subclass.");
    }

    // ---------------------------------------------------
    // ---------- End-Point: /dialogue/progress ----------
    // ---------------------------------------------------

    /**
     * Called after a successful call to the /dialogue/progress end-point.
     * @param {Boolean} dialogueContinues - whether or not the dialogue continues (true / false)
     * @param {DialogueStep} dialogueStep - if the dialogue continues, contains the next DialogueStep object to render.
     */
    handleProgressDialogue(dialogueContinues, dialogueStep) { 
        throw new Error("Method 'handleProgressDialogue()' must be implemented by a subclass.");
    }

    /**
     * Called after a failed call to the /dialogue/progress end-point.
     * @param {String} errorMessage a human-readable error message indicating the cause of the error.
     */
    handleProgressDialogueError(errorMessage) { 
        throw new Error("Method 'handleProgressDialogueError()' must be implemented by a subclass.");
    }

    // -------------------------------------------------
    // ---------- End-Point: /dialogue/cancel ----------
    // -------------------------------------------------

    /**
     * Called after a successful call to the /dialogue/cancel end-point.
     */
    handleCancelDialogue() {
        throw new Error("Method 'handleCancelDialogue()' must be implemented by a subclass.");
    }

    /**
     * Called after a failed call to the /dialogue/cancel end-point.
     * @param {String} errorMessage a human-readable error message indicating the cause of the error.
     */
    handleCancelDialogueError(errorMessage) {
        throw new Error("Method 'handleCancelDialogueError()' must be implemented by a subclass.");
    }

    // -------------------------------------------------------------------------------------------------
    // ---------- 3. Variables: End-points for retrieving or setting DialogueBranch Variables. ----------
    // --------------------------------------------------------------------------------------------------

    // -----------------------------------------------
    // ---------- End-Point: /variables/get ----------
    // -----------------------------------------------

    /**
     * Called after a successful call to the /variables/get end-point. Provides a List of Variable objects.
     * @param {List} the List of Variable objects, or an empty list.
     */
    handleGetVariables(variables) { 
        throw new Error("Method 'handleGetVariables' must be implemented by a subclass.");
    }

    /**
     * Called after a failed call to the /variables/get end-point.
     * @param {String} errorMessage a human-readable error message indicating the cause of the error.
     */
    handleGetVariablesError(errorMessage) {
        throw new Error("Method 'handleGetVariablesError' must be implemented by a subclass.");
     }

    // ------------------------------------------------------
    // ---------- End-Point: /variables/set-single ----------
    // ------------------------------------------------------

    /**
     * Called after a successful call to the /variables/set-single end-point.
     * 
     * @param {String} variableName - the name of the variable that was updated.
     */
    handleSetVariable(variableName) {
        throw new Error("Method 'handleSetVariable' must be implemented by a subclass.");
    }

    /**
     * Called after a failed call to the /variables/set-single end-point.
     * 
     * @param {String} variableName the name of the variable that was attempted to be updated.
     * @param {String} errorMessage a human-readable error message indicating the cause of the error.
     */
    handleSetVariableError(variableName, errorMessage) {
        throw new Error("Method 'handleSetVariableError' must be implemented by a subclass.");
    }

    // ---------------------------------------------------------------------------------------------------
    // ---------- 5. Information: End-points that provide information about the running service ----------
    // ---------------------------------------------------------------------------------------------------

    // --------------------------------------------
    // ---------- End-Point: /info/all ------------
    // --------------------------------------------

     /**
     * Called after a successful call to the /info/all end-point. Delivers a ServerInfo object,
     * containing the information about the running web service.
     * 
     * @param {ServerInfo} serverInfo - the ServerInfo object.
     */
    handleServerInfo(serverInfo) {
        throw new Error("Method 'handleServerInfo' must be implemented by a subclass.");
    }

    /**
     * Called after a failed call to the /info/all end-point.
     * 
     * @param {String} errorMessage a human-readable error message indicating the cause of the error.
     */
    handleServerInfoError(errorMessage) {
        throw new Error("Method 'handleServerInfoError' must be implemented by a subclass.");
    }

    // --------------------------------------------------------------------------------------------------------
    // ---------- 6. Admin: End-points for administrative control of the Dialogue Branch Web Service. ----------
    // ---------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------
    // ---------- End-Point: /admin/list-dialogues ----------
    // ------------------------------------------------------

    /**
     * Called after a successful call to the /admin/list-dialogues end-point.
     * @param {Array} dialogueNames - the list of names of dialogues received.
     */
    handleListDialogues(dialogueNames) { 
        throw new Error("Method 'handleListDialogues()' must be implemented by a subclass.");
    }

    /**
     * Called after a failed call to the /admin/list-dialogues end-point.
     * @param {String} errorMessage a human-readable error message indicating the cause of the error
     */
    handleListDialoguesError(errorMessage) { 
        throw new Error("Method 'handleListDialoguesError()' must be implemented by a subclass.");
    }

}
