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

import com.dialoguebranch.model.common.DialogueBranchConstants;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Node} represents a single step in a {@link Dialogue} definition.
 *
 * <p>Immutable: every field is set at construction and never changes afterward. {@link #getBody()}
 * is typed against {@link NodeContent} rather than concretely against {@link NodeBody}, since an
 * executed node (the result of {@code ActiveDialogue.executeNode()}) holds a
 * {@link ResolvedNodeBody} there instead — see {@link NodeContent}.</p>
 *
 * @author Harm op den Akker
 */
public class Node {

	private final @Nullable NodeHeader header;
	private final @Nullable NodeContent body;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link Node} with the given {@code header}.
	 *
	 * @param header the {@link NodeHeader} for this {@link Node}
	 */
	public Node(NodeHeader header) {
		this.header = header;
		this.body = null;
	}

	/**
	 * Creates an instance of a {@link Node} with the given {@code header} and {@code body}.
	 *
	 * @param header the {@link NodeHeader} for this {@link Node}
	 * @param body the body for this {@link Node} — a {@link NodeBody} before execution, a
	 * {@link ResolvedNodeBody} after.
	 */
	public Node(NodeHeader header, NodeContent body) {
		this.header = header;
		this.body = body;
	}

	/**
	 * Creates an instance of a {@link Node} instantiated with the contents from the given {@code other}
	 * {@link Node}.
	 *
	 * @param other the {@link Node} from which to copy its contents into this {@link Node}
	 */
	public Node(Node other) {
		this.header = other.header == null ? null : new NodeHeader(other.header);
		// A ResolvedNodeBody is copied by reference — resolved content is never mutated or
		// re-executed, so there's nothing a deep copy would protect against.
		this.body = other.body instanceof NodeBody otherBody ? new NodeBody(otherBody)
				: other.body;
	}

	// ------------------------------------------------- //
	// -------------------- Getters -------------------- //
	// ------------------------------------------------- //

	/**
	 * Returns the {@link NodeHeader} of this {@link Node}, or {@code null} if this node was
	 * created without one.
	 *
	 * @return the {@link NodeHeader} of this {@link Node}, or {@code null}.
	 */
	public @Nullable NodeHeader getHeader() {
		return header;
	}

	/**
	 * Returns the body of this {@link Node} — a {@link NodeBody} before execution, a
	 * {@link ResolvedNodeBody} after — or {@code null} if this node was created without one.
	 *
	 * @return the body of this {@link Node}, or {@code null}.
	 */
	public @Nullable NodeContent getBody() {
		return body;
	}

	// ------------------------------------------------- //
	// -------------------- Utility -------------------- //
	// ------------------------------------------------- //

	/**
	 * Returns the title of this {@link Node} as defined in its
	 * corresponding {@link NodeHeader}. Returns the same as {@code
	 * this.getHeader().getTitle()} or {@code null} if no {@link NodeHeader}
	 * has been set, or its title attribute is {@code null}.
	 *
	 * @return the title of this {@link Node} as defined in its
	 * corresponding {@link NodeHeader}.
	 */
	public @Nullable String getTitle() {
		if(header != null)
			return header.getTitle();
		else return null;
	}

	@Override
	public String toString() {
		return header + System.lineSeparator() +
				DialogueBranchConstants.DLB_HEADER_SEPARATOR + System.lineSeparator() + body;
	}

}
