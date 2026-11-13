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

import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ResourcePointer;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A dialogue as a {@link DialogueResolver} found it: which dialogue it is, its definition, and,
 * if its translation depends on the end user's gender, its definition for each gender (#400).
 *
 * @param pointer the dialogue's name and the language it is actually available in.
 * @param dialogue the dialogue's definition.
 * @param userGenderTranslations the dialogue's definition for each gender of the end user, or
 *                               {@code null} if it doesn't depend on it. Each must be the same
 *                               dialogue (same nodes), translated for that gender.
 * @author Harm op den Akker
 */
public record ResolvedDialogue(ResourcePointer pointer, Dialogue dialogue,
		@Nullable Function<TranslationContext.Gender, Dialogue> userGenderTranslations) {

	/**
	 * Creates a {@link ResolvedDialogue} whose translation doesn't depend on the end user's
	 * gender.
	 *
	 * @param pointer the dialogue's name and the language it is actually available in.
	 * @param dialogue the dialogue's definition.
	 */
	public ResolvedDialogue(ResourcePointer pointer, Dialogue dialogue) {
		this(pointer, dialogue, null);
	}
}
