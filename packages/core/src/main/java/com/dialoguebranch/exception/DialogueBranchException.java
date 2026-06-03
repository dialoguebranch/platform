/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
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

/**
 * An abstract representation of an {@link Exception} specifically for the Dialogue Branch context.
 * A {@link DialogueBranchException} does not add anything to the default {@link Exception} type,
 * and can be used simply to distinguish a class of exceptions that can be thrown by Dialogue Branch
 * software.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public abstract class DialogueBranchException extends Exception {

    // --------------------------------------------------------
    // -------------------- Constructor(s) --------------------
    // --------------------------------------------------------

    /**
     * Creates an instance of a {@link DialogueBranchException} with a given {@code message},
     * explaining the cause of the exception.
     *
     * @param message the message explaining the cause of the exception.
     */
    public DialogueBranchException(String message) {
        super(message);
    }

    /**
     * Creates an instance of a {@link DialogueBranchException} with a given {@code message},
     * explaining the cause of the exception, and the nested {@code cause}.
     *
     * @param message the message explaining the cause of the exception.
     * @param cause the actual nested {@link Throwable} cause of the exception.
     */
    public DialogueBranchException(String message, Throwable cause) {
        super(message,cause);
    }

}
