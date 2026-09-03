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

import com.dialoguebranch.expression.Expression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for the two-operand expression types (arithmetic, comparison and logical operators).
 * It holds the two operand expressions and implements the generic {@link Expression} tree methods
 * ({@link #getChildren()}, {@link #substituteChild(int, Expression)}, {@link #getDescendants()},
 * {@link #getVariableNames()}); subclasses only implement the operator's own evaluation.
 *
 * @author Dennis Hofs (RRD)
 */
public abstract class BinaryExpression implements Expression {

	/** The left-hand operand. */
	protected Expression operand1;

	/** The right-hand operand. */
	protected Expression operand2;

	/**
	 * Constructs a new binary expression.
	 *
	 * @param operand1 the left-hand operand.
	 * @param operand2 the right-hand operand.
	 */
	public BinaryExpression(Expression operand1, Expression operand2) {
		this.operand1 = operand1;
		this.operand2 = operand2;
	}

	/**
	 * Returns the left-hand operand.
	 *
	 * @return the left-hand operand.
	 */
	public Expression getOperand1() {
		return operand1;
	}

	/**
	 * Returns the right-hand operand.
	 *
	 * @return the right-hand operand.
	 */
	public Expression getOperand2() {
		return operand2;
	}

	@Override
	public List<Expression> getChildren() {
		List<Expression> result = new ArrayList<>();
		result.add(operand1);
		result.add(operand2);
		return result;
	}

	@Override
	public void substituteChild(int index, Expression expr) {
		if (index == 0)
			operand1 = expr;
		else if (index == 1)
			operand2 = expr;
	}

	@Override
	public List<Expression> getDescendants() {
		List<Expression> result = new ArrayList<>();
		for (Expression child : getChildren()) {
			result.add(child);
			result.addAll(child.getDescendants());
		}
		return result;
	}

	@Override
	public Set<String> getVariableNames() {
		Set<String> result = new HashSet<>();
		for (Expression child : getChildren()) {
			result.addAll(child.getVariableNames());
		}
		return result;
	}
}
