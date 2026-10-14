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

package com.dialoguebranch.web.service.controller.schema.authoring;

/**
 * The result of importing one translation file with {@code /import-translations} (#340).
 *
 * @param fileName          the uploaded file's name.
 * @param dialogueName      the draft dialogue it was imported as a translation of.
 * @param replaced          {@code true} if it replaced the dialogue's existing translation in
 *                          this language, {@code false} if the dialogue had none.
 * @param unmatchedEntries  the number of entries that match no translatable term of the
 *                          dialogue (speaker and text), kept anyway; {@code null} if the
 *                          dialogue's draft doesn't parse, so its terms are unknown.
 * @param untranslatedTerms the number of the dialogue's translatable terms the file has no
 *                          entry for; {@code null} if the dialogue's draft doesn't parse.
 * @author Harm op den Akker
 */
public record ImportedTranslation(String fileName, String dialogueName, boolean replaced,
		Integer unmatchedEntries, Integer untranslatedTerms) {
}
