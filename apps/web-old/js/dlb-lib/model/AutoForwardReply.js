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
 * An AutoForwardReply is a {@link Reply} option without a specific statement. An AutoForwardReply can be
 * interpreted in different ways by Dialogue Branch clients. For example, when a dialogue step only
 * has a single AutoForwardReply, the client can choose to "automatically forward" the dialogue,
 * following this reply (hence the name). Any dialogue step can only have 0 or 1 AutoForwardReply.
 * If the dialogue step has a combination of an AutoForwardReply and one or more BasicReply's, the
 * client can interpret the AutoForwardReply as a default selection that will automatically be
 * selected after some time, or as a default option that represents a user statement like "Say
 * Nothing". What an AutoForwardReply entails exactly is thus decided by the client developer and
 * the dialogue writer.
 * 
 * @extends Reply
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class AutoForwardReply extends Reply {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates an instance of an AutoForwardReply, instantiated with the given parameters.
     *
     * @constructor
     * @param {number} replyId The identifier of this reply (unique within its set).
     * @param {boolean} endsDialogue - Whether or not selecting this reply will lead to ending the dialogue.
     * @param {Array} actions The list of Actions associated with this reply.
     */
    constructor(replyId, endsDialogue, actions) {
        super(replyId, endsDialogue, actions);
    }

    /**
     * This method may be used to instantiate an 'empty' instance of an AutoForwardReply.
     * 
     * @returns a new, empty instance of an AutoForwardReply.
     */
    static emptyInstance() {
        return new AutoForwardReply(null, null, new Array());
    }

}
