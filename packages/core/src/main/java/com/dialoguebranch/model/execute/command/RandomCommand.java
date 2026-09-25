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
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResolvedNodeBody;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * This class models the &lt;&lt;random ...&gt;&gt; command in Dialogue Branch. It can be
 * part of a {@link NodeBody} (not inside a reply).
 *
 * @author Dennis Hofs
 */
public class RandomCommand extends AttributesCommand {
	private Random random = new Random();

	private final List<Clause> clauses;

	/**
	 * Creates a {@link RandomCommand} with the given {@code clauses}.
	 *
	 * @param clauses the clauses. There should be at least one.
	 */
	public RandomCommand(List<Clause> clauses) {
		this.clauses = Collections.unmodifiableList(new ArrayList<>(clauses));
	}

	/**
	 * Creates a deep copy of the given {@link RandomCommand}.
	 *
	 * @param other the command to copy.
	 */
	public RandomCommand(RandomCommand other) {
		List<Clause> copiedClauses = new ArrayList<>();
		for (Clause clause : other.clauses) {
			copiedClauses.add(new Clause(clause));
		}
		this.clauses = Collections.unmodifiableList(copiedClauses);
	}

	/**
	 * Returns the clauses. There should be at least one clause.
	 *
	 * @return the clauses
	 */
	public List<Clause> getClauses() {
		return clauses;
	}

	/**
	 * Replaces the source of randomness used by {@link #executeBodyCommand} to pick a clause.
	 * Package-private — the only intended caller is a test that needs deterministic
	 * {@code <<random>>} clause selection.
	 *
	 * @param random the {@link Random} to use.
	 */
	void setRandom(Random random) {
		this.random = random;
	}

	@Override
	public @Nullable Reply findReplyById(int replyId) {
		for (Clause clause : clauses) {
			Reply reply = clause.statement.findReplyById(replyId);
			if (reply != null)
				return reply;
		}
		return null;
	}

	@Override
	public void getReadVariableNames(Set<String> varNames) {
		for (Clause clause : clauses) {
			clause.statement.getReadVariableNames(varNames);
		}
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		for (Clause clause : clauses) {
			clause.statement.getWriteVariableNames(varNames);
		}
	}

	@Override
	public void getNodePointers(Set<NodePointer> pointers) {
		for (Clause clause : clauses) {
			clause.statement.getNodePointers(pointers);
		}
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			ResolvedNodeBody.Builder processedBody) throws EvaluationException {
		float totalWeight = 0;
		for (Clause clause : clauses) {
			totalWeight += clause.weight;
		}
		float selWeight = random.nextFloat() * totalWeight;
		float currWeight = 0;
		Clause selClause = null;
		for (int i = 0; selClause == null && i < clauses.size(); i++) {
			Clause clause = clauses.get(i);
			currWeight += clause.weight;
			if (selWeight <= currWeight)
				selClause = clause;
		}
		if (selClause == null)
			selClause = clauses.get(clauses.size() - 1);
		selClause.statement.execute(variables, processedBody);
	}

	@Override
	public String toString() {
		String newline = System.getProperty("line.separator");
		Clause clause = clauses.get(0);
		StringBuilder result = new StringBuilder("<<random");
		if (clause.weight != 1)
			result.append(" weight=\"" + clause.weight + "\"");
		result.append(">>" + newline);
		result.append(clause.statement + newline);
		for (int i = 1; i < clauses.size(); i++) {
			clause = clauses.get(i);
			result.append("<<or");
			if (clause.weight != 1)
				result.append(" weight=\"" + clause.weight + "\"");
			result.append(">>" + newline);
			result.append(clause.statement + newline);
		}
		result.append("<<endrandom>>");
		return result.toString();
	}

	/**
	 * Parses a {@link RandomCommand} from the token stream, consuming tokens from the opening
	 * {@code <<random>>} through the closing {@code <<endrandom>>}.
	 *
	 * @param cmdStartToken the token that started the {@code <<random>>} command.
	 * @param tokens the token iterator, positioned after the command-start token.
	 * @param nodeState the current node parse state.
	 * @return the parsed {@link RandomCommand}.
	 * @throws LineNumberParseException if the command is malformed or unterminated.
	 */
	public static RandomCommand parse(BodyToken cmdStartToken,
									  CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		Map<String, BodyToken> attrs = parseAttributesCommand(cmdStartToken,
				tokens);
		List<Clause> clauses = new ArrayList<>();
		Float weight = readFloatAttr("weight", attrs, cmdStartToken, false, 0f,
				null);
		if (weight == null)
			weight = 1f;
		while (true) {
			BodyParser bodyParser = new BodyParser(nodeState);
			BodyParser.ParseUntilCommandClauseResult bodyParse =
					bodyParser.parseUntilCommandClause(tokens,
					Arrays.asList("action", "if", "random", "set"),
					Arrays.asList("or", "endrandom"));
			if (bodyParse.cmdClauseStartToken == null) {
				throw new LineNumberParseException(
						"Command \"random\" not terminated",
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			}
			clauses.add(new Clause(weight, bodyParse.body));
			BodyToken clauseStartToken = bodyParse.cmdClauseStartToken;
			String clauseName = Objects.requireNonNull(bodyParse.cmdClauseName);
			attrs = parseAttributesCommand(clauseStartToken, tokens);
			switch (clauseName) {
			case "or":
				weight = readFloatAttr("weight", attrs, cmdStartToken, false,
						0f, null);
				if (weight == null)
					weight = 1f;
				break;
			case "endrandom":
				return new RandomCommand(clauses);
			}
		}
	}

	@Override
	public RandomCommand clone() {
		return new RandomCommand(this);
	}

	/**
	 * This class models a clause of a "random" statement. That is the "random"
	 * clause or an "or" clause.
	 */
	public static class Clause {
		private final float weight;
		private final NodeBody statement;

		/**
		 * Constructs a new clause.
		 *
		 * @param weight the weight for this clause
		 * @param statement the statement that should be output if this clause
		 * is selected
		 */
		public Clause(float weight, NodeBody statement) {
			this.weight = weight;
			this.statement = statement;
		}

		/**
		 * Creates a copy of the given {@link Clause}.
		 *
		 * @param other the clause to copy.
		 */
		public Clause(Clause other) {
			this.weight = other.weight;
			this.statement = new NodeBody(other.statement);
		}

		/**
		 * Returns the weight for this clause.
		 *
		 * @return the weight for this clause
		 */
		public float getWeight() {
			return weight;
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
