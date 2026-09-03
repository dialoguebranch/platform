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

package com.dialoguebranch.exception;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;

/**
 * This exception indicates a parse error within a Dialogue Branch node.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class NodeParseException extends ParseException {

	@Serial
	private static final long serialVersionUID = 1L;

	/** The name of the node from which this error occurred, or {@code null} if unknown. */
	private final @Nullable String nodeTitle;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Constructs a new exception in the specified node. If the node title is unknown, it can be set
	 * to {@code null}.
	 *
	 * @param message the message
	 * @param nodeTitle the node title or {@code null}
	 * @param cause the parse error
	 */
	public NodeParseException(String message, @Nullable String nodeTitle,
			LineNumberParseException cause) {
		super(message, cause);
		this.nodeTitle = nodeTitle;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the node title. If the title is unknown, this method returns {@code null}.
	 *
	 * @return the node title or {@code null}
	 */
	public @Nullable String getNodeTitle() {
		return nodeTitle;
	}

	/**
	 * Returns the cause of this {@link NodeParseException} as a
	 * {@link LineNumberParseException}.
	 *
	 * @return the {@link LineNumberParseException} that caused this {@link NodeParseException}
	 */
	public LineNumberParseException getLineNumberParseException() {
		// The constructor always requires a non-null LineNumberParseException cause.
		return (LineNumberParseException)Objects.requireNonNull(getCause());
	}

}
