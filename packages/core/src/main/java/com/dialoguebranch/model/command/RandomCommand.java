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

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.parser.NodeState;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.parser.BodyParser;
import com.dialoguebranch.parser.BodyToken;

import java.util.*;

/**
 * This class models the &lt;&lt;random ...&gt;&gt; command in DialogueBranch. It can be
 * part of a {@link NodeBody} (not inside a reply).
 * 
 * @author Dennis Hofs (RRD)
 */
public class RandomCommand extends AttributesCommand {
	private final Random random = new Random();

	private List<Clause> clauses = new ArrayList<>();

	public RandomCommand() {
	}

	public RandomCommand(RandomCommand other) {
		for (Clause clause : other.clauses) {
			this.clauses.add(new Clause(clause));
		}
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
	 * Sets the clauses. There should be at least one clause.
	 * 
	 * @param clauses the clauses
	 */
	public void setClauses(List<Clause> clauses) {
		this.clauses = clauses;
	}
	
	/**
	 * Adds a clause. There should be at least one clause.
	 * 
	 * @param clause the clause
	 */
	public void addClause(Clause clause) {
		clauses.add(clause);
	}

	@Override
	public Reply findReplyById(int replyId) {
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
			NodeBody processedBody) throws EvaluationException {
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
		selClause.statement.execute(variables, false, processedBody);
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

	public static RandomCommand parse(BodyToken cmdStartToken,
									  CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		Map<String, BodyToken> attrs = parseAttributesCommand(cmdStartToken,
				tokens);
		RandomCommand command = new RandomCommand();
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
			command.addClause(new Clause(weight, bodyParse.body));
			BodyToken clauseStartToken = bodyParse.cmdClauseStartToken;
			String clauseName = bodyParse.cmdClauseName;
			attrs = parseAttributesCommand(clauseStartToken, tokens);
			switch (clauseName) {
			case "or":
				weight = readFloatAttr("weight", attrs, cmdStartToken, false,
						0f, null);
				if (weight == null)
					weight = 1f;
				break;
			case "endrandom":
				return command;
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
		private float weight;
		private NodeBody statement;

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
		 * Sets the weight for this clause.
		 *
		 * @param weight the weight for this clause
		 */
		public void setWeight(float weight) {
			this.weight = weight;
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

		/**
		 * Sets the statement that should be output if the expression evaluates
		 * to true.
		 * 
		 * @param statement the statement
		 */
		public void setStatement(NodeBody statement) {
			this.statement = statement;
		}
	}
}
