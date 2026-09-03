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

import com.dialoguebranch.exception.LineNumberParseException;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.expression.Expression;
import com.dialoguebranch.expression.Token;
import com.dialoguebranch.expression.Value;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * An assignment expression {@code variable = value}. Evaluating it stores the right-hand value in
 * the variable map under the variable's name and returns that value. The first operand must be a
 * {@link ValueExpression} wrapping a {@link Token.Type#NAME} or {@link Token.Type#DOLLAR_VARIABLE}
 * token.
 *
 * @author Dennis Hofs (RRD)
 */
public class AssignExpression implements Expression {
	private Expression variableOperand;
	private String variableName;
	private Expression valueOperand;

	/**
	 * Constructs a new assignment expression.
	 *
	 * @param variableOperand the left-hand operand; must be a variable {@link ValueExpression}.
	 * @param operator the assignment operator token (used for error positions).
	 * @param valueOperand the right-hand operand, whose value is assigned.
	 * @throws LineNumberParseException if the left-hand operand is not a variable.
	 */
	public AssignExpression(Expression variableOperand, Token operator,
			Expression valueOperand) throws LineNumberParseException {
		if (!(variableOperand instanceof ValueExpression variableExpr)) {
			throw new LineNumberParseException(
					"First operand of assign expression must be a variable",
					operator.getLineNum(), operator.getColNum());
		}
		Token variableToken = variableExpr.getToken();
		if (variableToken.getType() != Token.Type.NAME &&
				variableToken.getType() != Token.Type.DOLLAR_VARIABLE) {
			throw new LineNumberParseException(
					"First operand of assign expression must be a variable",
					operator.getLineNum(), operator.getColNum());
		}
		this.variableOperand = variableOperand;
		this.variableName = Objects.requireNonNull(variableToken.getValue()).toString();
		this.valueOperand = valueOperand;
	}

	/**
	 * Returns the left-hand (variable) operand.
	 *
	 * @return the variable operand.
	 */
	public Expression getVariableOperand() {
		return variableOperand;
	}

	/**
	 * Returns the name of the variable being assigned.
	 *
	 * @return the variable name.
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * Returns the right-hand (value) operand.
	 *
	 * @return the value operand.
	 */
	public Expression getValueOperand() {
		return valueOperand;
	}

	@Override
	public Value evaluate(@Nullable Map<String,Object> variables)
			throws EvaluationException {
		Value result = valueOperand.evaluate(variables);
		if (variables != null)
			variables.put(variableName, result.getValue());
		return result;
	}

	@Override
	public List<Expression> getChildren() {
		List<Expression> result = new ArrayList<>();
		result.add(variableOperand);
		result.add(valueOperand);
		return result;
	}

	@Override
	public void substituteChild(int index, Expression expr) {
		if (index == 0)
			variableOperand = expr;
		else if (index == 1)
			valueOperand = expr;
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
		result.add(variableName);
		result.addAll(valueOperand.getVariableNames());
		return result;
	}

	@Override
	public String toString() {
		return variableOperand + " = " + valueOperand;
	}

	@Override
	public String toCode() {
		return variableOperand.toCode() + " = " + valueOperand.toCode();
	}
}
