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
 * A member-access expression {@code parent.name}. The parent must evaluate to a map; the
 * result is the value stored under {@code name} (or {@code null} if absent).
 *
 * <p>{@code getChildren()}/{@code substituteChild()}/{@code getDescendants()} are inherited
 * unchanged from {@link BinaryExpression}. {@code getVariableNames()} stays overridden below,
 * since — unlike a plain {@link BinaryExpression} — a literal field name on the right of the dot
 * (as opposed to a computed key, e.g. {@code a.$b}) is not itself a variable reference.</p>
 *
 * @author Dennis Hofs (RRD)
 */
public class DotExpression extends BinaryExpression {

	/**
	 * Constructs a new dot expression.
	 *
	 * @param parentOperand the operand left of the dot; must evaluate to a map.
	 * @param dotOperand the operand right of the dot (a name, or an expression yielding the key).
	 */
	public DotExpression(Expression parentOperand, Expression dotOperand) {
		super(parentOperand, dotOperand);
	}

	/**
	 * Returns the operand left of the dot.
	 *
	 * @return the parent operand.
	 */
	public Expression getParentOperand() {
		return getOperand1();
	}

	/**
	 * Returns the operand right of the dot.
	 *
	 * @return the dot operand.
	 */
	public Expression getDotOperand() {
		return getOperand2();
	}

	/**
	 * Returns the dot operand's literal field name if it's a bare {@link Token.Type#NAME} token
	 * (e.g. the {@code name} in {@code parent.name}), or {@code null} if it's a computed
	 * expression instead (e.g. {@code parent.$key}) — used by both {@link #evaluate} and
	 * {@link #getVariableNames} to agree on which case they're in.
	 */
	private @Nullable String dotOperandAsLiteralName() {
		if (getOperand2() instanceof ValueExpression valueExpr
				&& valueExpr.getToken().getType() == Token.Type.NAME) {
			return Objects.requireNonNull(valueExpr.getToken().getValue()).toString();
		}
		return null;
	}

	@Override
	public Value evaluate(@Nullable Map<String,Object> variables)
			throws EvaluationException {
		Value parent = getOperand1().evaluate(variables);
		if (!parent.isMap()) {
			throw new EvaluationException(
					"Dot parent must be a map, found: " +
					parent.getTypeString());
		}
		Map<?,?> map = (Map<?,?>) Objects.requireNonNull(parent.getValue());
		String name = dotOperandAsLiteralName();
		if (name == null) {
			Value nameVal = getOperand2().evaluate(variables);
			if (!nameVal.isString() && !nameVal.isNumber()) {
				throw new EvaluationException(
						"Dot name must be a string or number, found: " +
						nameVal.getTypeString());
			}
			name = nameVal.toString();
		}
		return new Value(map.get(name));
	}

	@Override
	public Set<String> getVariableNames() {
		Set<String> result = new HashSet<>(getOperand1().getVariableNames());
		if (dotOperandAsLiteralName() == null)
			result.addAll(getOperand2().getVariableNames());
		return result;
	}

	@Override
	public String toString() {
		return getOperand1() + "." + getOperand2();
	}

	@Override
	public String toCode() {
		return getOperand1().toCode() + "." + getOperand2().toCode();
	}
}
