/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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
 * The immutable runtime model of a parsed Dialogue Branch project. An
 * {@link com.dialoguebranch.model.execute.ExecutableProject} holds a project's
 * {@link com.dialoguebranch.model.execute.Dialogue}s; each {@link com.dialoguebranch.model.execute.Dialogue}
 * is a graph of {@link com.dialoguebranch.model.execute.Node}s, each with a
 * {@link com.dialoguebranch.model.execute.NodeHeader}, a
 * {@link com.dialoguebranch.model.execute.NodeBody} of statements and commands, and a set of
 * {@link com.dialoguebranch.model.execute.Reply} options.
 * {@link com.dialoguebranch.model.execute.LoggedDialogue} /
 * {@link com.dialoguebranch.model.execute.LoggedInteraction} record a completed run;
 * {@link com.dialoguebranch.model.execute.DialogueState} captures a run in progress.
 */
@NullMarked
package com.dialoguebranch.model.execute;

import org.jspecify.annotations.NullMarked;
