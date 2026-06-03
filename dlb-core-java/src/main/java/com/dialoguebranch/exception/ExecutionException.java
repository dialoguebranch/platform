/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

import java.io.Serial;

/**
 * An {@link ExecutionException} is an exception that can be thrown during execution of a Dialogue
 * Branch script.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ExecutionException extends DialogueBranchException {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * An {@link ExecutionException.Type} is a further refinement of the type of execution error
	 * that has occurred.
	 */
	public enum Type {
		AGENT_NOT_FOUND,
		DIALOGUE_NOT_FOUND,
		NODE_NOT_FOUND,
		REPLY_NOT_FOUND,
		INTERACTION_NOT_FOUND,
		NO_ACTIVE_DIALOGUE
	}

	/** The type of this {@link ExecutionException} as {@link Type}. */
	private final Type type;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of an {@link ExecutionException} with a given {@link Type} and
	 * {@code message}.
	 *
	 * @param type the type of the exception
	 * @param message the error message
	 */
	public ExecutionException(Type type, String message) {
		super(message);
		this.type = type;
	}

	/**
	 * Creates an instance of an {@link ExecutionException} with a given {@link Type},
	 * {@code message} and {@code cause}.
	 *
	 * @param type the type of the exception
	 * @param message the error message
	 * @param cause the cause of the exception
	 */
	public ExecutionException(Type type, String message, Throwable cause) {
		super(message, cause);
		this.type = type;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the {@link Type} of this {@link ExecutionException}.
	 * @return the {@link Type} of this {@link ExecutionException}.
	 */
	public Type getType() {
		return type;
	}

}
