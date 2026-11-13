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

/**
 * Describes a user's most recent interrupted (not finished or cancelled) dialogue session, as
 * returned by the Web Service's `/dialogue/get-ongoing` endpoint (`OngoingDialoguePayload`).
 */
export class OngoingDialogue {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * @param {string} dialogueName The name of the ongoing dialogue.
     * @param {string} loggedDialogueId The ongoing session's id — pass to
     * {@link DialogueBranchClient#continueDialogue}'s underlying `/dialogue/continue` call (via
     * `dialogueName`) or {@link DialogueBranchClient#cancelDialogue} to resume or end it.
     * @param {number} secondsSinceLastEngagement How many seconds ago the user last interacted
     * with this dialogue.
     * @param {?string} [language] The language code the dialogue runs in, or `null` from an older
     * Web Service that doesn't send it.
     */
    constructor(dialogueName, loggedDialogueId, secondsSinceLastEngagement, language = null) {
        this._dialogueName = dialogueName;
        this._loggedDialogueId = loggedDialogueId;
        this._secondsSinceLastEngagement = secondsSinceLastEngagement;
        this._language = language;
    }

    /**
     * Builds an OngoingDialogue from the JSON form the Web Service sends.
     *
     * @param {Object} json `{ dialogueName, loggedDialogueId, secondsSinceLastEngagement, language }`.
     * @returns {OngoingDialogue} The parsed ongoing-dialogue info.
     */
    static fromJSON(json) {
        return new OngoingDialogue(json.dialogueName, json.loggedDialogueId,
            json.secondsSinceLastEngagement, json.language ?? null);
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    set dialogueName(dialogueName) {
        this._dialogueName = dialogueName;
    }

    get dialogueName() {
        return this._dialogueName;
    }

    set loggedDialogueId(loggedDialogueId) {
        this._loggedDialogueId = loggedDialogueId;
    }

    get loggedDialogueId() {
        return this._loggedDialogueId;
    }

    set secondsSinceLastEngagement(secondsSinceLastEngagement) {
        this._secondsSinceLastEngagement = secondsSinceLastEngagement;
    }

    get secondsSinceLastEngagement() {
        return this._secondsSinceLastEngagement;
    }

    set language(language) {
        this._language = language;
    }

    get language() {
        return this._language;
    }

}
