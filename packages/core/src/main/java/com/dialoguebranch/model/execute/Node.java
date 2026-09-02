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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.model.common.DialogueBranchConstants;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Node} represents a single step in a {@link Dialogue} definition.
 *
 * @author Harm op den Akker
 */
public class Node {

	private @Nullable NodeHeader header;
	private @Nullable NodeBody body;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link Node}.
	 */
	public Node() { }

	/**
	 * Creates an instance of a {@link Node} with the given {@code header}.
	 *
	 * @param header the {@link NodeHeader} for this {@link Node}
	 */
	public Node(NodeHeader header) {
		this.header = header;
	}

	/**
	 * Creates an instance of a {@link Node} with the given {@code header} and {@code body}.
	 *
	 * @param header the {@link NodeHeader} for this {@link Node}
	 * @param body the {@link NodeBody} for this {@link Node}
	 */
	public Node(NodeHeader header, NodeBody body) {
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
		if (other.header != null)
			header = new NodeHeader(other.header);
		if (other.body != null)
			body = new NodeBody(other.body);
	}

	// ------------------------------------------------- //
	// -------------------- Getters -------------------- //
	// ------------------------------------------------- //

	/**
	 * Returns the {@link NodeHeader} of this {@link Node}, or {@code null} if this node was
	 * created without one and none has been set since.
	 *
	 * @return the {@link NodeHeader} of this {@link Node}, or {@code null}.
	 */
	public @Nullable NodeHeader getHeader() {
		return header;
	}

	/**
	 * Returns the {@link NodeBody} of this {@link Node}, or {@code null} if this node was created
	 * without one and none has been set since.
	 *
	 * @return the {@link NodeBody} of this {@link Node}, or {@code null}.
	 */
	public @Nullable NodeBody getBody() {
		return body;
	}

	// ------------------------------------------------- //
	// -------------------- Setters -------------------- //
	// ------------------------------------------------- //

	/**
	 * Sets the {@link NodeHeader} for this {@link Node}.
	 *
	 * @param header the {@link NodeHeader} for this {@link Node}.
	 */
	public void setHeader(NodeHeader header) {
		this.header = header;
	}

	/**
	 * Sets the {@link NodeBody} for this {@link Node}.
	 *
	 * @param body the {@link NodeBody} for this {@link Node}.
	 */
	public void setBody(NodeBody body) {
		this.body = body;
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
