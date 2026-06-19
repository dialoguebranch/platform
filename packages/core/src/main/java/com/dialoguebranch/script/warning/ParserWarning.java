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

package com.dialoguebranch.script.warning;

/**
 * Represents a non-fatal warning produced by a fault-tolerant parser (such as
 * {@link com.dialoguebranch.script.parser.EditableHeaderParser} or
 * {@link com.dialoguebranch.script.parser.EditableBodyParser}). A warning carries the line number
 * at which the issue was detected and a human-readable message describing it.
 *
 * @author Harm op den Akker
 */
public class ParserWarning {

    private int lineNumber;
    private String message;

    /**
     * Creates a {@link ParserWarning} with the given line number and message.
     * @param lineNumber the line number at which the issue was detected (1-based; 0 if unknown).
     * @param message a human-readable description of the warning.
     */
    public ParserWarning(int lineNumber, String message) {
        this.lineNumber = lineNumber;
        this.message = message;
    }

    /**
     * Returns the line number at which the issue was detected.
     * @return the 1-based line number, or 0 if unknown.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Sets the line number at which the issue was detected.
     * @param lineNumber the 1-based line number.
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Returns the human-readable description of this warning.
     * @return the warning message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the human-readable description of this warning.
     * @param message the warning message.
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
