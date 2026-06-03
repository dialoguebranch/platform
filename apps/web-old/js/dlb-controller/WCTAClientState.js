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

import { User } from '../dlb-lib/model/User.js';
import { ClientState } from '../dlb-lib/ClientState.js';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';

export const INTERACTION_TESTER_STYLE_TEXT = "TEXT";
export const INTERACTION_TESTER_STYLE_BALLOONS = "BALLOONS";

/**
 * The WCTAClientState is the client-specific ClientState object for the Dialogue Branch Web Client Test Application.
 *
 * @extends ClientState
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class WCTAClientState extends ClientState {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates an instance of a WCTAClientState object to keep track of the state of the web client test application.
     * Log information is passed through the provided Logger instance.
     * @param {Logger} logger A Logger instance that may be used to log information.
     */
    constructor(logger) {
        super(logger);
        this._LOGTAG = "WCTAClientState";
        this._variableBrowserExtended = false;
        this._dialogueBrowserExtended = false;
        this._interactionTesterStyle = INTERACTION_TESTER_STYLE_TEXT;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    // ----- debugConsoleVisible

    /**
     * Sets a boolean value indicating whether or not the debug console is currently visible.
     * @param {boolean} debugConsoleVisible a boolean value indicating whether or not the debug console is currently visible.
     */
    set debugConsoleVisible(debugConsoleVisible) {
        this._debugConsoleVisible = debugConsoleVisible;
        DocumentFunctions.setCookie('state.debugConsoleVisible', this._debugConsoleVisible, 365);
    }

    /**
     * Returns a boolean value indicating whether or not the debug console is currently visible.
     * @returns a boolean value indicating whether or not the debug console is currently visible.
     */
    get debugConsoleVisible() {
        return this._debugConsoleVisible;
    }

    // ----- variableBrowserExtended

    /**
     * Sets a boolean value indicating whether the Variable Browser is currently extended or not.
     * @param {Boolean} variableBrowserExtended - true if the Variable Browser is currently extended, false otherwise.
     */
    set variableBrowserExtended(variableBrowserExtended) {
        this._variableBrowserExtended = variableBrowserExtended;
        DocumentFunctions.setCookie('state.variableBrowserExtended', this._variableBrowserExtended, 365);
    }

    /**
     * Returns a boolean value indicating whether the Variable Browser is currently extended or not.
     * @returns a boolean value indicating whether the Variable Browser is currently extended or not.
     */
    get variableBrowserExtended() {
        return this._variableBrowserExtended;
    }

    // ----- dialogueBrowserExtended

    /**
     * Sets a boolean value indicating whether the Dialogue Browser is currently extended or not.
     * @param {Boolean} dialogueBrowserExtended - true if the Dialogue Browser is currently extended, false otherwise.
     */
    set dialogueBrowserExtended(dialogueBrowserExtended) {
        this._dialogueBrowserExtended = dialogueBrowserExtended;
        DocumentFunctions.setCookie('state.dialogueBrowserExtended', this._dialogueBrowserExtended, 365);
    }

    /**
     * Returns a boolean value indicating whether the Dialogue Browser is currently extended or not.
     * @returns a boolean value indicating whether the Dialogue Browser is currently extended or not.
     */
    get dialogueBrowserExtended() {
        return this._dialogueBrowserExtended;
    }

    // ----- interactionTesterStyle

    /**
     * Sets the style of the interaction tester (as either 'text' or 'balloons' style).
     * @param {String} interactionTesterStyle - the style value for the interaction tester.
     */
    set interactionTesterStyle(interactionTesterStyle) {
        this._interactionTesterStyle = interactionTesterStyle;
        DocumentFunctions.setCookie('state.interactionTesterStyle', this._interactionTesterStyle, 365);
    }

    /**
     * Returns the style of the interaction tester (as either 'text' or 'balloons' style).
     * @returns the style of the interaction tester (as either 'text' or 'balloons' style).
     */
    get interactionTesterStyle() {
        return this._interactionTesterStyle;
    }

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------

    /**
     * Loads information about the ClientState from a cookie, if set.
     */
    loadFromCookie() {
        var cookieValue = DocumentFunctions.getCookie('state.debugConsoleVisible');
        if(cookieValue == "true") this._debugConsoleVisible = true;
        else this._debugConsoleVisible = false;

        cookieValue = DocumentFunctions.getCookie('state.variableBrowserExtended');
        if(cookieValue == "true") this._variableBrowserExtended = true;
        else this._variableBrowserExtended = false;

        cookieValue = DocumentFunctions.getCookie('state.dialogueBrowserExtended');
        if(cookieValue == "true") this._dialogueBrowserExtended = true;
        else this._dialogueBrowserExtended = false;

        cookieValue = DocumentFunctions.getCookie('state.interactionTesterStyle');
        if(cookieValue != null) {
            if(cookieValue == INTERACTION_TESTER_STYLE_TEXT || cookieValue == INTERACTION_TESTER_STYLE_BALLOONS) {
                this._interactionTesterStyle = cookieValue;
                this.logger.debug(this._LOGTAG, "Found a valid cookie-stored value for 'state.interactionTesterStyle': "+cookieValue);
            }
        }

        var cookieUserName = DocumentFunctions.getCookie('user.name');
        var cookieUserRole = DocumentFunctions.getCookie('user.role');
        var cookieUserAuthToken = DocumentFunctions.getCookie('user.authToken');

        // All variables are non-empty / non-null
        if(cookieUserName && cookieUserRole && cookieUserAuthToken) {
            var user = new User(cookieUserName, cookieUserRole, cookieUserAuthToken);
            this._user = user;
        }
    }

}
