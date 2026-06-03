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

import { Reply } from './Reply.js';

/**
 * A BasicReply is an implementation of the Reply class and represents the most basic of reply
 * options that allows the user to 'say something' in reply to an Agent statement in a dialogue
 * step.
 *
 * @extends Reply
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class BasicReply extends Reply {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates an instance of a BasicReply with the given parameters.
     *
     * @param {number} replyId The identifier of this reply option that is unique within this
     * dialogue step.
     * @param {boolean} endsDialogue Whether or not selecting this reply will end the dialogue.
     * @param {Array} actions A list of Actions that should be executed when this reply is chosen.
     * @param {String} statement The statement that the user 'utters' when selecting this Reply.
     */
    constructor(replyId, endsDialogue, actions, statement) {
        super(replyId, endsDialogue, actions);
        this._statement = statement;
    }

    /**
     * Returns a new instance of a BasicReply objects that is 'empty'. The object is instantiated
     * with null values and an empty list of actions.
     * 
     * @returns a new, empty instance of a BasicReply.
     */
    static emptyInstance() {
        return new BasicReply(null, null, new Array(), null);
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    /**
     * Sets the statement that the user makes in reply to the agent when selecting this reply.
     *
     * @param {String} statement The statement that the user makes in reply to the agent when
     * selecting this reply.
     */
    set statement(statement) {
        this._statement = statement;
    }

    /**
     * Returns the statement that the user makes in reply to the agent when selecting this reply.
     * 
     * @returns the statement that the user makes in reply to the agent when selecting this reply.
     */
    get statement() {
        return this._statement;
    }

}
