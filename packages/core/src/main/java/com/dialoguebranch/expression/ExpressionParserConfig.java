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

package com.dialoguebranch.expression;

/**
 * Configuration for a {@link Tokenizer} / {@link ExpressionParser}: which variable syntaxes are
 * recognised and whether a single {@code =} is accepted as assignment. Defaults: plain variables
 * allowed, {@code $}-variables not, single {@code =} not.
 *
 * @author Dennis Hofs (RRD)
 */
public class ExpressionParserConfig {
	private boolean allowPlainVariables = true;
	private boolean allowDollarVariables = false;
	private boolean allowSingleEquals = false;

	/** Constructs a configuration with the default settings. */
	public ExpressionParserConfig() {
	}

	/**
	 * Returns whether unadorned identifiers are recognised as variable references.
	 *
	 * @return {@code true} if plain variables are allowed.
	 */
	public boolean isAllowPlainVariables() {
		return allowPlainVariables;
	}

	/**
	 * Sets whether unadorned identifiers are recognised as variable references.
	 *
	 * @param allowPlainVariables {@code true} to allow plain variables.
	 */
	public void setAllowPlainVariables(boolean allowPlainVariables) {
		this.allowPlainVariables = allowPlainVariables;
	}

	/**
	 * Returns whether {@code $}-prefixed tokens are recognised as variable references.
	 *
	 * @return {@code true} if {@code $}-variables are allowed.
	 */
	public boolean isAllowDollarVariables() {
		return allowDollarVariables;
	}

	/**
	 * Sets whether {@code $}-prefixed tokens are recognised as variable references.
	 *
	 * @param allowDollarVariables {@code true} to allow {@code $}-variables.
	 */
	public void setAllowDollarVariables(boolean allowDollarVariables) {
		this.allowDollarVariables = allowDollarVariables;
	}

	/**
	 * Returns whether a single {@code =} is accepted as the assignment operator (in addition to
	 * {@code ==} being equality).
	 *
	 * @return {@code true} if a single {@code =} is allowed.
	 */
	public boolean isAllowSingleEquals() {
		return allowSingleEquals;
	}

	/**
	 * Sets whether a single {@code =} is accepted as the assignment operator.
	 *
	 * @param allowSingleEquals {@code true} to allow a single {@code =}.
	 */
	public void setAllowSingleEquals(boolean allowSingleEquals) {
		this.allowSingleEquals = allowSingleEquals;
	}
}
