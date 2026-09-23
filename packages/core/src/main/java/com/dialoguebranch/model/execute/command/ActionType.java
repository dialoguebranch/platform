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

package com.dialoguebranch.model.execute.command;

import org.jspecify.annotations.Nullable;

/**
 * The four kinds of {@link ActionCommand} the {@code <<action type="..." ...>>} command supports.
 * Each constant's {@link #getWireValue() wireValue} is the lowercase string used both in {@code
 * .dlb} script source (the {@code type} attribute) and in the web-service wire protocol ({@link
 * com.dialoguebranch.model.execute.protocol.DialogueAction#getType()}) — kept as an explicit
 * mapping (not {@link Enum#name()}) so that contract stays stable regardless of how the constants
 * themselves are named or ordered.
 *
 * @author Harm op den Akker
 */
public enum ActionType {

	/** An image to display. */
	IMAGE("image"),

	/** A video to play. */
	VIDEO("video"),

	/** A hyperlink. */
	LINK("link"),

	/** A client-defined action, free-form by design — see {@link ActionCommand}. */
	GENERIC("generic");

	private final String wireValue;

	ActionType(String wireValue) {
		this.wireValue = wireValue;
	}

	/**
	 * Returns the lowercase wire value for this type (e.g. {@code "image"}), as used in both
	 * {@code .dlb} script source and the web-service wire protocol.
	 *
	 * @return the wire value.
	 */
	public String getWireValue() {
		return wireValue;
	}

	@Override
	public String toString() {
		return wireValue;
	}

	/**
	 * Returns the {@link ActionType} whose {@link #getWireValue() wireValue} matches the given
	 * string, or {@code null} if it matches none of them.
	 *
	 * @param wireValue the wire value to look up (e.g. {@code "image"}).
	 * @return the matching {@link ActionType}, or {@code null}.
	 */
	public static @Nullable ActionType fromWireValue(String wireValue) {
		for (ActionType type : values()) {
			if (type.wireValue.equals(wireValue))
				return type;
		}
		return null;
	}
}
