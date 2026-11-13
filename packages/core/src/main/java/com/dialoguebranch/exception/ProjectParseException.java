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

package com.dialoguebranch.exception;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thrown when a project can't be loaded because one or more of its files have parse errors. It
 * lists every error, per file, so that a single exception tells the whole story; its message
 * names each file and error, one per line.
 *
 * @author Harm op den Akker
 */
public class ProjectParseException extends ParseException {

	@Serial
	private static final long serialVersionUID = -4061297318046528710L;

	/** The parse errors, per file (in the order they were found). */
	private final Map<String, List<ParseException>> errors;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates a {@link ProjectParseException} for the given project and its parse errors.
	 *
	 * @param projectLocation where the project was loaded from, for the message.
	 * @param errors the parse errors, per file; must not be empty.
	 */
	public ProjectParseException(String projectLocation, Map<String, List<ParseException>> errors) {
		super(createMessage(projectLocation, errors));
		Map<String, List<ParseException>> copy = new LinkedHashMap<>();
		errors.forEach((file, list) -> copy.put(file, List.copyOf(list)));
		this.errors = Collections.unmodifiableMap(copy);
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the parse errors, per file.
	 *
	 * @return the parse errors, per file.
	 */
	public Map<String, List<ParseException>> getErrors() {
		return errors;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	private static String createMessage(String projectLocation,
			Map<String, List<ParseException>> errors) {
		int count = errors.values().stream().mapToInt(List::size).sum();
		StringBuilder message = new StringBuilder("Project at ").append(projectLocation)
				.append(" has ").append(count).append(count == 1 ? " parse error:" : " parse errors:");
		errors.forEach((file, list) -> {
			for (ParseException error : list)
				message.append("\n  ").append(file).append(": ").append(error.getMessage());
		});
		return message.toString();
	}
}
