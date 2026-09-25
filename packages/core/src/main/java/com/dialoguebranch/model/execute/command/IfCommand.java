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

import com.dialoguebranch.exception.LineNumberParseException;
import com.dialoguebranch.execution.parser.BodyParser;
import com.dialoguebranch.execution.parser.BodyToken;
import com.dialoguebranch.execution.parser.NodeState;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.expression.Expression;
import com.dialoguebranch.expression.Value;
import com.dialoguebranch.expression.types.AssignExpression;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResolvedNodeBody;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * This class models the &lt;&lt;if ...&gt;&gt; command in Dialogue Branch. It can be part
 * of a {@link NodeBody} (not inside a reply).
 *
 * @author Dennis Hofs
 */
public class IfCommand extends ExpressionCommand {
	private final List<Clause> ifClauses;
	private final @Nullable NodeBody elseClause;

	/**
	 * Creates an {@link IfCommand} with the given {@code ifClauses} and {@code elseClause}.
	 *
	 * @param ifClauses the if clauses, processed from first to last. There should be at least
	 * one clause — the "if" clause; any subsequent clauses are "elseif" clauses.
	 * @param elseClause the else clause, or {@code null} if there is none.
	 */
	public IfCommand(List<Clause> ifClauses, @Nullable NodeBody elseClause) {
		this.ifClauses = Collections.unmodifiableList(new ArrayList<>(ifClauses));
		this.elseClause = elseClause;
	}

	/**
	 * Creates a deep copy of the given {@link IfCommand}.
	 *
	 * @param other the {@link IfCommand} to copy.
	 */
	public IfCommand(IfCommand other) {
		List<Clause> copiedClauses = new ArrayList<>();
		for (Clause ifClause : other.ifClauses) {
			copiedClauses.add(new Clause(ifClause));
		}
		this.ifClauses = Collections.unmodifiableList(copiedClauses);
		this.elseClause = other.elseClause == null ? null : new NodeBody(other.elseClause);
	}

	/**
	 * Returns the if clauses. They should be processed from first to last.
	 * There should be at least one clause. That is the "if" clause. Any
	 * subsequent clauses are "elseif" clauses.
	 *
	 * @return the if clauses
	 */
	public List<Clause> getIfClauses() {
		return ifClauses;
	}

	/**
	 * Returns the else clause. If there is no else clause, then this method
	 * returns null (default).
	 *
	 * @return the else clause or null
	 */
	public @Nullable NodeBody getElseClause() {
		return elseClause;
	}

	@Override
	public @Nullable Reply findReplyById(int replyId) {
		for (Clause clause : ifClauses) {
			Reply reply = clause.statement.findReplyById(replyId);
			if (reply != null)
				return reply;
		}
		if (elseClause != null)
			return elseClause.findReplyById(replyId);
		else
			return null;
	}

	@Override
	public void getReadVariableNames(Set<String> varNames) {
		for (Clause clause : ifClauses) {
			varNames.addAll(clause.expression.getVariableNames());
			clause.statement.getReadVariableNames(varNames);
		}
		if (elseClause != null)
			elseClause.getReadVariableNames(varNames);
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		for (Clause clause : ifClauses) {
			clause.statement.getWriteVariableNames(varNames);
		}
		if (elseClause != null)
			elseClause.getWriteVariableNames(varNames);
	}

	@Override
	public void getNodePointers(Set<NodePointer> pointers) {
		for (Clause clause : ifClauses) {
			clause.statement.getNodePointers(pointers);
		}
		if (elseClause != null)
			elseClause.getNodePointers(pointers);
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			ResolvedNodeBody.Builder processedBody) throws EvaluationException {
		for (Clause clause : ifClauses) {
			Value clauseEval = clause.expression.evaluate(variables);
			if (clauseEval.asBoolean()) {
				clause.statement.execute(variables, processedBody);
				return;
			}
		}
		if (elseClause != null)
			elseClause.execute(variables, processedBody);
	}

	@Override
	public String toString() {
		String newline = System.getProperty("line.separator");
		Clause clause = ifClauses.get(0);
		StringBuilder result = new StringBuilder(
				"<<if " + clause.expression + ">>" + newline);
		result.append(clause.statement + newline);
		for (int i = 1; i < ifClauses.size(); i++) {
			clause = ifClauses.get(i);
			result.append("<<elseif " + clause.expression + ">>" + newline);
			result.append(clause.statement + newline);
		}
		if (elseClause != null) {
			result.append("<<else>>" + newline);
			result.append(elseClause + newline);
		}
		result.append("<<endif>>");
		return result.toString();
	}

	/**
	 * Parses an {@link IfCommand} from the token stream, consuming tokens from the opening
	 * {@code <<if>>} through the closing {@code <<endif>>}.
	 *
	 * @param cmdStartToken the token that started the {@code <<if>>} command.
	 * @param tokens the token iterator, positioned after the command-start token.
	 * @param nodeState the current node parse state.
	 * @return the parsed {@link IfCommand}.
	 * @throws LineNumberParseException if the command is malformed or unterminated.
	 */
	public static IfCommand parse(BodyToken cmdStartToken,
								  CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		List<Clause> ifClauses = new ArrayList<>();
		NodeBody elseClause = null;
		ReadContentResult content = readCommandContent(cmdStartToken, tokens);
		ParseContentResult parsedIf = parseCommandContentExpression(
				cmdStartToken, content, "if");
		checkNoAssignment(cmdStartToken, parsedIf.name, parsedIf.expression);
		while (true) {
			BodyParser bodyParser = new BodyParser(nodeState);
			BodyParser.ParseUntilCommandClauseResult bodyParse =
					bodyParser.parseUntilCommandClause(tokens,
					Arrays.asList("action", "if", "random", "set"),
					Arrays.asList("elseif", "else", "endif"));
			if (bodyParse.cmdClauseStartToken == null) {
				throw new LineNumberParseException(
						"Command \"if\" not terminated",
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			}
			if (parsedIf.name.equals("if") || parsedIf.name.equals("elseif")) {
				ifClauses.add(new Clause(
						Objects.requireNonNull(parsedIf.expression),
						bodyParse.body));
			} else {
				elseClause = bodyParse.body;
			}
			BodyToken clauseStartToken = bodyParse.cmdClauseStartToken;
			String clauseName = Objects.requireNonNull(bodyParse.cmdClauseName);
			content = readCommandContent(clauseStartToken, tokens);
			switch (clauseName) {
			case "elseif":
				if (elseClause != null) {
					throw new LineNumberParseException(
							"Found \"elseif\" after \"else\"",
							clauseStartToken.getLineNumber(),
							clauseStartToken.getColNumber());
				}
				parsedIf = parseCommandContentExpression(clauseStartToken,
						content, clauseName);
				checkNoAssignment(clauseStartToken, parsedIf.name,
						parsedIf.expression);
				break;
			case "else":
				if (elseClause != null) {
					throw new LineNumberParseException(
							"Found more than one \"else\"",
							clauseStartToken.getLineNumber(),
							clauseStartToken.getColNumber());
				}
				parsedIf = parseCommandContentName(clauseStartToken, content,
						clauseName);
				break;
			case "endif":
				parseCommandContentName(clauseStartToken, content, clauseName);
				return new IfCommand(ifClauses, elseClause);
			}
		}
	}

	private static void checkNoAssignment(BodyToken cmdStartToken,
										  String name, @Nullable Expression expression)
			throws LineNumberParseException {
		if (expression == null)
			return;
		List<Expression> list = new ArrayList<>();
		list.add(expression);
		list.addAll(expression.getDescendants());
		for (Expression expr : list) {
			if (expr instanceof AssignExpression) {
				throw new LineNumberParseException(String.format(
						"Found assignment expression in \"%s\" command", name),
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			}
		}
	}

	@Override
	public IfCommand clone() {
		return new IfCommand(this);
	}

	/**
	 * This class models a clause of an if statement. That is the "if" clause
	 * or an "elseif" clause.
	 */
	public static class Clause {
		private final Expression expression;
		private final NodeBody statement;

		/**
		 * Constructs a new if clause.
		 *
		 * @param expression the if expression that should be evaluated as a
		 * boolean
		 * @param statement the statement that should be output if the
		 * expression evaluates to true
		 */
		public Clause(Expression expression, NodeBody statement) {
			this.expression = expression;
			this.statement = statement;
		}

		/**
		 * Creates a copy of the given {@link Clause}.
		 *
		 * @param other the {@link Clause} to copy.
		 */
		public Clause(Clause other) {
			this.expression = other.expression;
			this.statement = new NodeBody(other.statement);
		}

		/**
		 * Returns the if expression that should be evaluated as a boolean.
		 *
		 * @return the if expression
		 */
		public Expression getExpression() {
			return expression;
		}

		/**
		 * Returns the statement that should be output if the expression
		 * evaluates to true.
		 *
		 * @return the statement
		 */
		public NodeBody getStatement() {
			return statement;
		}

	}
}
