/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

package com.dialoguebranch.model.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.parser.NodeState;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;
import nl.rrd.utils.expressions.Expression;
import nl.rrd.utils.expressions.types.AssignExpression;
import com.dialoguebranch.parser.BodyToken;

/**
 * This class models a &lt;&lt;set ...&gt;&gt; command. It can be part of a
 * {@link NodeBody} (along with an agent statement) or a {@link
 * Reply} (to be performed when the user chooses the reply). It
 * contains an assign statement.
 * 
 * @author Dennis Hofs (RRD)
 */
public class SetCommand extends ExpressionCommand {
	private AssignExpression expression;
	
	public SetCommand(AssignExpression expression) {
		this.expression = expression;
	}

	public SetCommand(SetCommand other) {
		this.expression = other.expression;
	}

	public AssignExpression getExpression() {
		return expression;
	}

	public void setExpression(AssignExpression expression) {
		this.expression = expression;
	}
	
	@Override
	public Reply findReplyById(int replyId) {
		return null;
	}

	@Override
	public void getReadVariableNames(Set<String> varNames) {
		varNames.addAll(expression.getValueOperand().getVariableNames());
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		varNames.add(expression.getVariableName());
	}

	@Override
	public void getNodePointers(Set<NodePointer> pointers) {
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			NodeBody processedBody) throws EvaluationException {
		expression.evaluate(variables);
	}

	@Override
	public String toString() {
		return "<<set " + expression + ">>";
	}

	@Override
	public SetCommand clone() {
		return new SetCommand(this);
	}

	public static SetCommand parse(BodyToken cmdStartToken,
								   CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		ReadContentResult content = readCommandContent(cmdStartToken, tokens);
		ParseContentResult parsed = parseCommandContentExpression(cmdStartToken,
				content, "set");
		if (!(parsed.expression instanceof AssignExpression)) {
			throw new LineNumberParseException(
					"Expression in \"set\" command is not an assignment",
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		AssignExpression assignExpr = (AssignExpression)parsed.expression;
		checkNoAssignment(cmdStartToken, assignExpr.getValueOperand());
		return new SetCommand(assignExpr);
	}

	private static void checkNoAssignment(BodyToken cmdStartToken,
                                          Expression expression) throws LineNumberParseException {
		List<Expression> list = new ArrayList<>();
		list.add(expression);
		list.addAll(expression.getDescendants());
		for (Expression expr : list) {
			if (expr instanceof AssignExpression) {
				throw new LineNumberParseException(
						"Found assignment expression in value operand of \"set\" command",
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			}
		}
	}
}
