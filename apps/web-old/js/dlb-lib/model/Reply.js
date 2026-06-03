/* @license
 *
 *                   Copyright (c) 2023 Fruit Tree Labs (www.fruittreelabs.com)
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

/**
 * A Reply is an 'abstract' class that should not be instantiated directly by the client
 * application. It is an abstract representation of a possible 'reply' that a user can give in any
 * given dialogue step. The specific implementations of Reply are {@link AutoForwardReply} and
 * {@link BasicReply}.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class Reply {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates an instance of a Reply. Note that 'Reply' is an 'abstract' class that should not be
     * instantiated directly. Instead, use the constrcutor of one of the classes that extends Reply.
     * @param {number} replyId The identifier of this reply option that is unique within this dialogue step.
     * @param {boolean} endsDialogue Whether or not selecting this reply will end the dialogue.
     * @param {Array} actions A list of Actions that should be executed when this reply is chosen.
     */
    constructor(replyId, endsDialogue, actions) {
        this._replyId = replyId;
        this._endsDialogue = endsDialogue;
        this._actions = actions;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    /**
     * Sets the replyId for this Reply.
     * @param {number} replyId The identifier of this reply option that is unique within this dialogue step.
     */
    set replyId(replyId) {
        this._replyId = replyId;
    }

    /**
     * Returns the replyId for this Reply.
     * @returns the replyId for this Reply.
     */
    get replyId() {
        return this._replyId;
    }

    /**
     * Sets whether or not choosing this reply will end the dialogue.
     * @param {boolean} endsDialogue Whether or not selecting this reply will end the dialogue.
     */
    set endsDialogue(endsDialogue) {
        this._endsDialogue = endsDialogue;
    }

    /**
     * Returns whether or not choosing this reply will end the dialogue.
     * @returns whether or not choosing this reply will end the dialogue.
     */
    get endsDialogue() {
        return this._endsDialogue;
    }

    /**
     * Sets the list of Actions that should be executed when this Reply is chosen.
     * @param {Array} actions A list of Actions that should be executed when this reply is chosen.
     */
    set actions(actions) {
        this._actions = actions;
    }

    /**
     * Returns the list of actions that should be executed when this reply is chosen.
     * @returns the list of actions that should be executed when this reply is chosen.
     */
    get actions() {
        return this._actions;
    }

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------
    
    /**
     * Returns a human readable String representation of this Reply object.
     * @returns a human readable String representation of this Reply object.
     */
    toString() {
        var result = "";
        result += "\n\t\t{replyId: " + this._replyId;
        result += "\n\t\tendsDialogue: " + this._endsDialogue;
        result += "\n\t\tactions: " + this._actions +"}";
        return result;
    }

}
