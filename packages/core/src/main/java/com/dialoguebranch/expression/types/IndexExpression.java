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
import com.dialoguebranch.expression.Value;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * An index expression {@code parent[index]}. The parent must evaluate to a string, list or map;
 * the result is the element / character / value at the given index or key.
 *
 * @author Dennis Hofs (RRD)
 */
public class IndexExpression implements Expression {
	private Expression parentOperand;
	private Expression indexOperand;

	/**
	 * Constructs a new index expression.
	 *
	 * @param parentOperand the operand being indexed; must evaluate to a string, list or map.
	 * @param indexOperand the index or key operand.
	 */
	public IndexExpression(Expression parentOperand, Expression indexOperand) {
		this.parentOperand = parentOperand;
		this.indexOperand = indexOperand;
	}

	/**
	 * Returns the operand being indexed.
	 *
	 * @return the parent operand.
	 */
	public Expression getParentOperand() {
		return parentOperand;
	}

	/**
	 * Returns the index or key operand.
	 *
	 * @return the index operand.
	 */
	public Expression getIndexOperand() {
		return indexOperand;
	}

	@Override
	public Value evaluate(@Nullable Map<String,Object> variables)
			throws EvaluationException {
		Value parentVal = parentOperand.evaluate(variables);
		if (!parentVal.isString() && !parentVal.isList() &&
				!parentVal.isMap()) {
			throw new EvaluationException(
					"Index parent must be a string, list or map, found: " +
					parentVal.getTypeString());
		}
		Value indexVal = indexOperand.evaluate(variables);
		if (parentVal.isString()) {
			if (!indexVal.isNumericString() && !indexVal.isNumber()) {
				throw new EvaluationException(
						"String index must be a number or numeric string, found: " +
						indexVal.getTypeString());
			}
			Number num = indexVal.asNumber();
			if (!(num instanceof Integer)) {
				throw new EvaluationException(
						"String index must be an integer, found: " +
						num.getClass().getSimpleName());
			}
			return new Value(Character.toString(parentVal.toString().charAt(
					num.intValue())));
		} else if (parentVal.isList()) {
			if (!indexVal.isNumericString() && !indexVal.isNumber()) {
				throw new EvaluationException(
						"List index must be a number or numeric string, found: " +
						indexVal.getTypeString());
			}
			Number num = indexVal.asNumber();
			if (!(num instanceof Integer)) {
				throw new EvaluationException(
						"List index must be an integer, found: " +
						num.getClass().getSimpleName());
			}
			List<?> list = (List<?>) Objects.requireNonNull(parentVal.getValue());
			return new Value(list.get(num.intValue()));
		} else {
			if (!indexVal.isString() && !indexVal.isNumber()) {
				throw new EvaluationException(
						"Map index must be a string or number, found: " +
						indexVal.getTypeString());
			}
			Map<?,?> map = (Map<?,?>) Objects.requireNonNull(parentVal.getValue());
			return new Value(map.get(indexVal.toString()));
		}
	}

	@Override
	public List<Expression> getChildren() {
		List<Expression> result = new ArrayList<>();
		result.add(parentOperand);
		result.add(indexOperand);
		return result;
	}

	@Override
	public void substituteChild(int index, Expression expr) {
		if (index == 0)
			parentOperand = expr;
		else if (index == 1)
			indexOperand = expr;
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

	@Override
	public String toString() {
		return parentOperand + "[" + indexOperand + "]";
	}

	@Override
	public String toCode() {
		return parentOperand.toCode() + "[" + indexOperand.toCode() + "]";
	}
}
