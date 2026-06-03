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

export class DialogueStep {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor(dialogueName, node, speaker, statement, replies, loggedDialogueId, loggedInteractionIndex) {
        this._dialogueName = dialogueName;
        this._node = node;
        this._speaker = speaker;
        this._statement = statement;
        this._replies = replies;
        this._loggedDialogueId = loggedDialogueId;
        this._loggedInteractionIndex = loggedInteractionIndex;
    }

    static emptyInstance() {
        return new DialogueStep(null,null,null,null,new Array(),null,null);
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

    set node(node) {
        this._node = node;
    }

    get node() {
        return this._node;
    }

    set speaker(speaker) {
        this._speaker = speaker;
    }

    get speaker() {
        return this._speaker;
    }

    set statement(statement) {
        this._statement = statement;
    }

    get statement() {
        return this._statement;
    }

    set replies(replies) {
        this._replies = replies;
    }

    get replies() {
        return this._replies;
    }

    set loggedDialogueId(loggedDialogueId) {
        this._loggedDialogueId = loggedDialogueId;
    }

    get loggedDialogueId() {
        return this._loggedDialogueId;
    }

    set loggedInteractionIndex(loggedInteractionIndex) {
        this._loggedInteractionIndex = loggedInteractionIndex;
    }

    get loggedInteractionIndex() {
        return this._loggedInteractionIndex;
    }

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------

    addReply(reply) {
        this._replies.push(reply);
    }

    toString() {
        var result = "DialogueStep [";
        result += "\n\tdialogueName: " + this._dialogueName;
        result += "\n\tnode: " + this._node;
        result += "\n\tspeaker: " + this._speaker;
        result += "\n\tloggedDialogueId: " + this._loggedDialogueId;
        result += "\n\tloggedInteractionIndex: " + this._loggedInteractionIndex;
        result += "\n\tstatement: " + this._statement.toString();
        result += "\n\treplies: [";
        for(var i=0; i< this._replies.length; i++) {
            result += this._replies[i].toString();
            if(i < this._replies.length - 1) {
                result += ",";
            }
        }
        result += "\n\t]"

        result += "\n]";
        return result;
    }
    
}
