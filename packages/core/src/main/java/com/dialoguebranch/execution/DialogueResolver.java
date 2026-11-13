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
 * Portions of this file are vendored from the rrd-utils library
 * (https://github.com/RoessinghResearch/rrd-utils), used under the MIT License.
 *
 * Copyright (c) 2022 Roessingh Research and Development
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

package com.dialoguebranch.execution;

import com.dialoguebranch.exception.ExecutionException;
import org.jspecify.annotations.Nullable;

/**
 * Finds a dialogue by its name and language, for an {@link ActiveDialogue} that follows a reply
 * into another dialogue (#473). An {@link com.dialoguebranch.model.execute.ExecutableProject} is
 * one; an application that keeps its dialogues elsewhere, such as the Web Service with its draft
 * and published content, supplies its own.
 *
 * @author Harm op den Akker
 */
@FunctionalInterface
public interface DialogueResolver {

	/**
	 * Returns the dialogue with the given name, in the given language. If the dialogue isn't
	 * available in that language, it should be returned in the source language.
	 *
	 * @param dialogueName the dialogue's name, including its folders, such as {@code
	 *                     "intro/welcome"}.
	 * @param language the language code, or {@code null} for the source language.
	 * @return the dialogue.
	 * @throws ExecutionException ({@link ExecutionException.Type#DIALOGUE_NOT_FOUND}) if there
	 *                            is no dialogue with that name.
	 */
	ResolvedDialogue resolve(String dialogueName, @Nullable String language)
			throws ExecutionException;
}
