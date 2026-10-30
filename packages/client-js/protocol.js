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

// Side-effect-free re-export of the wire-protocol model types and their fromJSON parsers, with
// no transport code — so a wrapping backend and its own front end can share these types without
// pulling in DialogueBranchClient/fetch at all. See #88's Scope section for the rationale.
//
// One exception: Reply has no fromJSON of its own — it's never meant to be instantiated
// directly (see its own docs); use BasicReply.fromJSON/AutoForwardReply.fromJSON, or
// DialogueStep.fromJSON, which already dispatches between them for you when parsing a full step.

export { Action } from './model/Action.js';
export { AutoForwardReply } from './model/AutoForwardReply.js';
export { BasicReply } from './model/BasicReply.js';
export { DialogueStep } from './model/DialogueStep.js';
export { OngoingDialogue } from './model/OngoingDialogue.js';
export { Reply } from './model/Reply.js';
export { Segment } from './model/Segment.js';
export { ServerInfo } from './model/ServerInfo.js';
export { Statement } from './model/Statement.js';
export { SupportedVariable } from './model/SupportedVariable.js';
export { User } from './model/User.js';
export { Variable } from './model/Variable.js';
export { VariableHistoryEntry } from './model/VariableHistoryEntry.js';
