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
        this._interactionTesterStyle = INTERACTION_TESTER_STYLE_BALLOONS;
        this._selectedProject = null;
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

        cookieValue = DocumentFunctions.getCookie('state.interactionTesterStyle');
        if(cookieValue != null) {
            if(cookieValue == INTERACTION_TESTER_STYLE_TEXT || cookieValue == INTERACTION_TESTER_STYLE_BALLOONS) {
                this._interactionTesterStyle = cookieValue;
                this.logger.debug(this._LOGTAG, "Found a valid cookie-stored value for 'state.interactionTesterStyle': "+cookieValue);
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
