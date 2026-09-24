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

package com.dialoguebranch.model.execute;

import java.util.List;

/**
 * The common read surface shared by {@link NodeBody} (script-stage content, as parsed — may
 * contain any {@link com.dialoguebranch.model.execute.command.Command}) and
 * {@link ResolvedNodeBody} (execution-stage content, as sent to a client — may only contain an
 * {@link com.dialoguebranch.model.execute.command.ActionCommand} or
 * {@link com.dialoguebranch.model.execute.command.InputCommand}).
 *
 * <p>A field that legitimately holds either one depending on lifecycle stage — {@link
 * Node#getBody()} (script content before execution, resolved content after) and
 * {@link Reply#getStatement()} (same distinction, one level down) — is typed against this
 * interface rather than concretely against {@link NodeBody}. Code that already knows which stage
 * it's holding (e.g. anything that just called {@code execute()}) should narrow with a cast rather
 * than route everything through this minimal surface.</p>
 *
 * @author Harm op den Akker
 */
public interface NodeContent {

	/**
	 * Returns the segments as an unmodifiable list.
	 *
	 * @return the segments as an unmodifiable list
	 */
	List<NodeBody.Segment> getSegments();

	/**
	 * Returns the replies as an unmodifiable list.
	 *
	 * @return the replies as an unmodifiable list
	 */
	List<Reply> getReplies();
}
