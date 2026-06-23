/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.execution;

import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.LoggedDialogue;
import com.dialoguebranch.model.execute.Node;

/**
 * An immutable value object returned after executing a node in an {@link ActiveDialogue}. It
 * bundles together the four pieces of state that callers typically need after a node execution step.
 *
 * @param dialogue         the {@link Dialogue} definition that was being executed
 * @param node             the {@link Node} that was executed and returned to the client
 * @param loggedDialogue   the {@link LoggedDialogue} entry recording this interaction in the
 *                         dialogue history
 * @param interactionIndex the zero-based index of this interaction within the
 *                         {@code loggedDialogue}
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 */
public record ExecuteNodeResult(Dialogue dialogue, Node node,
                                LoggedDialogue loggedDialogue, int interactionIndex) { }
