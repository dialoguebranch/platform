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
 * Parsers that turn {@code .dlb} source and {@code dlb-project.xml} metadata into the immutable
 * runtime model in {@link com.dialoguebranch.model.execute}.
 * {@link com.dialoguebranch.execution.parser.DialogueBranchParser} parses a single dialogue
 * (delegating node bodies to {@link com.dialoguebranch.execution.parser.BodyParser},
 * {@link com.dialoguebranch.execution.parser.CommandParser} and
 * {@link com.dialoguebranch.execution.parser.ReplyParser});
 * {@link com.dialoguebranch.execution.parser.ProjectParser} parses a whole project and resolves
 * cross-references between dialogues. {@link com.dialoguebranch.execution.parser.ScriptLoader}
 * abstracts where the source is read from (a directory, a classpath resource, or a
 * caller-supplied backend).
 */
@NullMarked
package com.dialoguebranch.execution.parser;

import org.jspecify.annotations.NullMarked;
