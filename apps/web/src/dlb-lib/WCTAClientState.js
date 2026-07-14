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

import { ClientState } from '../dlb-lib/ClientState.js';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';

export const DIALOGUE_WORKSPACE_STYLE_TEXT = "TEXT";
export const DIALOGUE_WORKSPACE_STYLE_BALLOONS = "BALLOONS";
export const DIALOGUE_WORKSPACE_STYLE_EDIT = "EDIT";

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
        this._dialogueWorkspaceStyle = DIALOGUE_WORKSPACE_STYLE_BALLOONS;
        this._selectedProject = null;
        this._debugConsoleShowApi = true;
        this._debugConsoleShowEvents = true;
        this._debugConsoleShowCookies = false;
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

    // ----- debugConsoleWidth / debugConsoleHeight

    /**
     * Sets the width (in pixels) of the Debug Console panel.
     * @param {Number} debugConsoleWidth - the width, in pixels, of the Debug Console panel.
     */
    set debugConsoleWidth(debugConsoleWidth) {
        this._debugConsoleWidth = debugConsoleWidth;
        DocumentFunctions.setCookie('state.debugConsoleWidth', this._debugConsoleWidth, 365);
    }

    /**
     * Returns the width (in pixels) of the Debug Console panel, or undefined if never set.
     * @returns the width, in pixels, of the Debug Console panel.
     */
    get debugConsoleWidth() {
        return this._debugConsoleWidth;
    }

    /**
     * Sets the height (in pixels) of the Debug Console panel.
     * @param {Number} debugConsoleHeight - the height, in pixels, of the Debug Console panel.
     */
    set debugConsoleHeight(debugConsoleHeight) {
        this._debugConsoleHeight = debugConsoleHeight;
        DocumentFunctions.setCookie('state.debugConsoleHeight', this._debugConsoleHeight, 365);
    }

    /**
     * Returns the height (in pixels) of the Debug Console panel, or undefined if never set.
     * @returns the height, in pixels, of the Debug Console panel.
     */
    get debugConsoleHeight() {
        return this._debugConsoleHeight;
    }

    // ----- debugConsoleShowApi / debugConsoleShowEvents / debugConsoleShowCookies

    /**
     * Sets whether the "API Calls" filter of the Debug Console is enabled.
     * @param {Boolean} debugConsoleShowApi - true if API call entries should be shown.
     */
    set debugConsoleShowApi(debugConsoleShowApi) {
        this._debugConsoleShowApi = debugConsoleShowApi;
        DocumentFunctions.setCookie('state.debugConsoleShowApi', this._debugConsoleShowApi, 365);
    }

    /**
     * Returns whether the "API Calls" filter of the Debug Console is enabled.
     * @returns true if API call entries should be shown.
     */
    get debugConsoleShowApi() {
        return this._debugConsoleShowApi;
    }

    /**
     * Sets whether the "Events" filter of the Debug Console is enabled.
     * @param {Boolean} debugConsoleShowEvents - true if event entries should be shown.
     */
    set debugConsoleShowEvents(debugConsoleShowEvents) {
        this._debugConsoleShowEvents = debugConsoleShowEvents;
        DocumentFunctions.setCookie('state.debugConsoleShowEvents', this._debugConsoleShowEvents, 365);
    }

    /**
     * Returns whether the "Events" filter of the Debug Console is enabled.
     * @returns true if event entries should be shown.
     */
    get debugConsoleShowEvents() {
        return this._debugConsoleShowEvents;
    }

    /**
     * Sets whether the "Cookies" sub-panel of the Debug Console is enabled.
     * @param {Boolean} debugConsoleShowCookies - true if the Cookies sub-panel should be shown.
     */
    set debugConsoleShowCookies(debugConsoleShowCookies) {
        this._debugConsoleShowCookies = debugConsoleShowCookies;
        DocumentFunctions.setCookie('state.debugConsoleShowCookies', this._debugConsoleShowCookies, 365);
    }

    /**
     * Returns whether the "Cookies" sub-panel of the Debug Console is enabled.
     * @returns true if the Cookies sub-panel should be shown.
     */
    get debugConsoleShowCookies() {
        return this._debugConsoleShowCookies;
    }

    // ----- selectedProject

    set selectedProject(selectedProject) {
        this._selectedProject = selectedProject;
        if (selectedProject?.slug) {
            // Only the slug is persisted — it's the only part the app actually needs to
            // resume the session; displayName is UI decoration re-fetched on load (see
            // MainPage.vue).
            DocumentFunctions.setCookie('state.selectedProject', selectedProject.slug, 365);
        } else {
            DocumentFunctions.deleteCookie('state.selectedProject');
        }
    }

    get selectedProject() {
        return this._selectedProject;
    }

    // ----- dialogueWorkspaceStyle

    /**
     * Sets the style of the dialogue workspace (one of DIALOGUE_WORKSPACE_STYLE_TEXT,
     * _BALLOONS, or _EDIT).
     * @param {String} dialogueWorkspaceStyle - the style value for the dialogue workspace.
     */
    set dialogueWorkspaceStyle(dialogueWorkspaceStyle) {
        this._dialogueWorkspaceStyle = dialogueWorkspaceStyle;
        DocumentFunctions.setCookie('state.dialogueWorkspaceStyle', this._dialogueWorkspaceStyle, 365);
    }

    /**
     * Returns the style of the dialogue workspace (one of DIALOGUE_WORKSPACE_STYLE_TEXT,
     * _BALLOONS, or _EDIT).
     * @returns the style of the dialogue workspace.
     */
    get dialogueWorkspaceStyle() {
        return this._dialogueWorkspaceStyle;
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

        cookieValue = DocumentFunctions.getCookie('state.debugConsoleWidth');
        if(cookieValue) this._debugConsoleWidth = parseInt(cookieValue);

        cookieValue = DocumentFunctions.getCookie('state.debugConsoleHeight');
        if(cookieValue) this._debugConsoleHeight = parseInt(cookieValue);

        // These three default to true/true/false (set in the constructor); only override that
        // default when a cookie with a valid boolean value is actually present.
        cookieValue = DocumentFunctions.getCookie('state.debugConsoleShowApi');
        if(cookieValue == "true" || cookieValue == "false") this._debugConsoleShowApi = cookieValue == "true";

        cookieValue = DocumentFunctions.getCookie('state.debugConsoleShowEvents');
        if(cookieValue == "true" || cookieValue == "false") this._debugConsoleShowEvents = cookieValue == "true";

        cookieValue = DocumentFunctions.getCookie('state.debugConsoleShowCookies');
        if(cookieValue == "true" || cookieValue == "false") this._debugConsoleShowCookies = cookieValue == "true";

        cookieValue = DocumentFunctions.getCookie('state.dialogueWorkspaceStyle');
        if(cookieValue != null) {
            if(cookieValue == DIALOGUE_WORKSPACE_STYLE_TEXT || cookieValue == DIALOGUE_WORKSPACE_STYLE_BALLOONS
                    || cookieValue == DIALOGUE_WORKSPACE_STYLE_EDIT) {
                this._dialogueWorkspaceStyle = cookieValue;
                this.logger.debug(this._LOGTAG, "Found a valid cookie-stored value for 'state.dialogueWorkspaceStyle': "+cookieValue);
            }
        }

        cookieValue = DocumentFunctions.getCookie('state.selectedProject');
        if (cookieValue) {
            // Only the slug was persisted; displayName gets backfilled on load (see MainPage.vue).
            this._selectedProject = { slug: cookieValue };
        }

        // Authentication state is no longer stored in cookies — it is derived from the Keycloak
        // token at application boot (see src/main.js and src/keycloak.js).
    }

}
