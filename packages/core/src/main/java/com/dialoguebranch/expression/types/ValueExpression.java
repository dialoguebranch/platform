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

package com.dialoguebranch.expression.types;

import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.expression.Expression;
import com.dialoguebranch.expression.Token;
import com.dialoguebranch.expression.Value;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * A leaf expression wrapping a single atom {@link Token}: a literal ({@link Token.Type#STRING},
 * {@link Token.Type#NUMBER}, {@link Token.Type#BOOLEAN}, {@link Token.Type#NULL}) or a variable
 * reference ({@link Token.Type#NAME}, {@link Token.Type#DOLLAR_VARIABLE}). A variable that is not
 * in the variable map evaluates to {@code null}.
 *
 * @author Dennis Hofs (RRD)
 */
public class ValueExpression implements Expression {
	private Token token;

	/**
	 * Constructs a new value expression.
	 *
	 * @param token the atom token this expression wraps.
	 */
	public ValueExpression(Token token) {
		this.token = token;
	}

	/**
	 * Returns the atom token this expression wraps.
	 *
	 * @return the token.
	 */
	public Token getToken() {
		return token;
	}

	@Override
	public Value evaluate(@Nullable Map<String,Object> variables)
			throws EvaluationException {
		if (token.getType() == Token.Type.NAME ||
				token.getType() == Token.Type.DOLLAR_VARIABLE) {
			if (variables == null)
				return new Value(null);
			else
				return new Value(variables.get(Objects.requireNonNull(token.getValue()).toString()));
		} else {
			return Objects.requireNonNull(token.getValue());
		}
	}

	@Override
	public List<Expression> getChildren() {
		return new ArrayList<>();
	}

	@Override
	public void substituteChild(int index, Expression expr) {
	}

	@Override
	public List<Expression> getDescendants() {
		return new ArrayList<>();
	}

	@Override
	public Set<String> getVariableNames() {
		Set<String> result = new HashSet<>();
		if (token.getType() == Token.Type.NAME ||
				token.getType() == Token.Type.DOLLAR_VARIABLE) {
			result.add(Objects.requireNonNull(token.getValue()).toString());
		}
		return result;
	}

	@Override
	public String toString() {
		return token.getText();
	}

	@Override
	public String toCode() {
		return token.getText();
	}
}
