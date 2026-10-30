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


package com.dialoguebranch.execution.parser;

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.model.common.ReservedVariables;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.ResourcePointer;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Reports an error for every node that stores into a reserved {@code $dlb.} variable that isn't
 * defined (#400) — used by {@link ProjectParser}, split out as one of its independent validation
 * passes. A {@code <<set>>} is already checked when it is parsed; this catches the input commands
 * of the user's replies, which store what the user enters.
 *
 * @author Harm op den Akker
 */
class ReservedVariableValidator {

	/** Utility class — no instances. */
	private ReservedVariableValidator() {
	}

	/**
	 * @param allParsedDialogues every dialogue that parsed at all, keyed by its source file.
	 * @param onError called with the originating file and an error for each such node.
	 */
	static void validate(Map<ResourcePointer, Dialogue> allParsedDialogues,
						 BiConsumer<ResourcePointer, ParseException> onError) {
		for (Map.Entry<ResourcePointer, Dialogue> entry : allParsedDialogues.entrySet()) {
			for (Node node : entry.getValue().getNodes()) {
				Set<String> written = new LinkedHashSet<>();
				((NodeBody) Objects.requireNonNull(node.getBody())).getWriteVariableNames(written);
				for (String name : written) {
					String problem = ReservedVariables.checkName(name);
					if (problem != null) {
						onError.accept(entry.getKey(), new ParseException(
								"Node \"" + node.getTitle() + "\": " + problem));
					}
				}
			}
		}
	}
}
