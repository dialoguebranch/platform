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

package com.dialoguebranch.i18n;

import com.dialoguebranch.execution.model.NodeBody;

/**
 * A {@link SourceTranslatable} pairs a {@link Translatable} segment with the identity of its
 * speaker and addressee, providing the conversational context needed to select the right
 * gender-inflected or speaker-specific translation variant.
 *
 * <p>Instances are produced by {@link TranslatableExtractor} when it walks a {@link
 * NodeBody NodeBody}, and are consumed by {@link Translator} to look up
 * and apply the correct translation from a translation map.</p>
 *
 * @param speaker   the name of the agent delivering the statement, or {@link #USER} when the
 *                  statement belongs to the end-user.
 * @param addressee the name of the agent being addressed, or {@link #USER} when the end-user is
 *                  being addressed.
 * @param translatable the {@link Translatable} segment (text, variables, and optional
 *                     {@code <<input>>} commands) that may need to be translated.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public record SourceTranslatable(String speaker, String addressee, Translatable translatable) {

	/** The string to use when the speaker of a statement is the user of the system. */
	public static final String USER = "_user";
}
