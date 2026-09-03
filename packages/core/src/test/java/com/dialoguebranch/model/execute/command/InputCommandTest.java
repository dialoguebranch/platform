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

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Parses each {@code <<input ...>>} variant out of a fixture dialogue and checks attribute
 * parsing, {@code getParameters()}, {@code toString()}, {@code executeBodyCommand}, the statement
 * log, {@code clone()} and the parse-error paths. Part of the #90 area-B test hardening (#155).
 */
public class InputCommandTest {

	private static final ZonedDateTime NOW =
			ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneId.of("Europe/Lisbon"));

	private static final String FIXTURE = """
		title: Start
		tags:
		speaker: Robot
		---
		Choose an input to try.
		[[Name: <<input type="text" value="$name" min="2" max="40" allowSpaces="false" capWords="true" description="your name">>|Next]]
		[[Bio: <<input type="longtext" value="$bio" max="500">>|Next]]
		[[Email: <<input type="email" value="$email">>|Next]]
		[[Age: <<input type="numeric" value="$age" min="0" max="120">>|Next]]
		[[Wake: <<input type="time" value="$wake" granularityMinutes="15" startTime="07:30" maxTime="now">>|Next]]
		[[Pets: <<input type="set" value1="$dog" option1="Dog" value2="$cat" option2="Cat">>|Next]]
		===
		title: Next
		tags:
		speaker: Robot
		---
		Thanks.
		[[Done.|End]]
		===
		title: End
		tags:
		speaker:
		---

		===
		""";

	private Dialogue dialogue;

	@Before
	public void setUp() throws Exception {
		dialogue = parse(FIXTURE);
	}

	private static Dialogue parse(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("inputs", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue();
		}
	}

	private static List<ParseException> parseErrors(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("bad", new StringReader(dlb))) {
			return parser.readDialogue().getParseErrors();
		}
	}

	/** Pulls the single input command out of the reply statement at the given index of Start. */
	private InputCommand inputCommand(int replyIndex) {
		List<Reply> replies = dialogue.getNodeById("Start").getBody().getReplies();
		NodeBody statement = replies.get(replyIndex).getStatement();
		assertNotNull(statement);
		for (NodeBody.Segment segment : statement.getSegments()) {
			if (segment instanceof NodeBody.CommandSegment cmd
					&& cmd.getCommand() instanceof InputCommand input) {
				return input;
			}
		}
		throw new AssertionError("no input command in reply " + replyIndex);
	}

	// ---------------------------------------------------------------- //
	// -------------------- text ---------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void textInputParsesAllAttributes() {
		InputAbstractTextCommand text = (InputAbstractTextCommand) inputCommand(0);

		assertEquals(InputCommand.TYPE_TEXT, text.getType());
		assertEquals("name", text.getVariableName());
		assertEquals(Integer.valueOf(2), text.getMin());
		assertEquals(Integer.valueOf(40), text.getMax());
		assertEquals(Boolean.FALSE, text.getAllowSpaces());
		assertEquals(Boolean.TRUE, text.getCapWords());
		assertEquals(Boolean.TRUE, text.getAllowNumbers()); // default kept
		assertEquals("your name", text.getDescription());
	}

	@Test
	public void textInputParametersAndStatementLog() {
		InputAbstractTextCommand text = (InputAbstractTextCommand) inputCommand(0);

		Map<String, ?> params = text.getParameters();
		assertEquals("name", params.get("variableName"));
		assertEquals(2, params.get("min"));
		assertEquals(40, params.get("max"));
		assertEquals(Boolean.FALSE, params.get("allowSpaces"));

		VariableStore store = new VariableStore(new User("u"));
		store.setValue("name", "Ada", false, NOW);
		assertEquals("Ada", text.getStatementLog(store));
	}

	@Test
	public void textInputToStringRoundTripsTheKeyAttributes() {
		String s = inputCommand(0).toString();
		assertTrue(s, s.contains("type=\"text\""));
		assertTrue(s, s.contains("value=\"$name\""));
		assertTrue(s, s.contains("min=\"2\""));
		assertTrue(s, s.contains("max=\"40\""));
		assertTrue(s, s.contains("allowSpaces=\"false\""));
		assertTrue(s, s.contains("description=\"your name\""));
	}

	@Test
	public void textInputWriteVariableNamesAndExecute() throws Exception {
		InputAbstractTextCommand text = (InputAbstractTextCommand) inputCommand(0);

		Set<String> writes = new HashSet<>();
		text.getWriteVariableNames(writes);
		assertEquals(Set.of("name"), writes);

		NodeBody processed = new NodeBody();
		text.executeBodyCommand(new LinkedHashMap<>(), processed);
		assertEquals(1, processed.getSegments().size());
		assertTrue(processed.getSegments().get(0) instanceof NodeBody.CommandSegment);
	}

	@Test
	public void textInputCloneIsAnIndependentCopy() {
		InputAbstractTextCommand original = (InputAbstractTextCommand) inputCommand(0);
		InputAbstractTextCommand copy = (InputAbstractTextCommand) original.clone();
		copy.setVariableName("other");
		assertEquals("name", original.getVariableName());
		assertEquals("other", copy.getVariableName());
	}

	// ---------------------------------------------------------------- //
	// -------------------- longtext / email --------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void longtextInput() {
		InputAbstractTextCommand longtext = (InputAbstractTextCommand) inputCommand(1);
		assertEquals(InputCommand.TYPE_LONGTEXT, longtext.getType());
		assertEquals("bio", longtext.getVariableName());
		assertEquals(Integer.valueOf(500), longtext.getMax());
		assertTrue(longtext instanceof InputLongtextCommand);
	}

	@Test
	public void emailInput() {
		InputCommand email = inputCommand(2);
		assertEquals(InputCommand.TYPE_EMAIL, email.getType());
		assertTrue(email instanceof InputEmailCommand);
		assertEquals("email", ((InputEmailCommand) email).getVariableName());
		assertEquals("email", email.getParameters().get("variableName"));
		assertTrue(email.toString().contains("type=\"email\""));
	}

	// ---------------------------------------------------------------- //
	// -------------------- numeric ------------------------------ //
	// ---------------------------------------------------------------- //

	@Test
	public void numericInputParsesRangeAndAlwaysReportsMinMax() {
		InputNumericCommand numeric = (InputNumericCommand) inputCommand(3);
		assertEquals(InputCommand.TYPE_NUMERIC, numeric.getType());
		assertEquals("age", numeric.getVariableName());
		assertEquals(Integer.valueOf(0), numeric.getMin());
		assertEquals(Integer.valueOf(120), numeric.getMax());

		Map<String, ?> params = numeric.getParameters();
		assertTrue(params.containsKey("min"));
		assertTrue(params.containsKey("max"));
		assertEquals(0, params.get("min"));
	}

	@Test
	public void numericStatementLogReadsTheStoredNumber() {
		InputNumericCommand numeric = (InputNumericCommand) inputCommand(3);
		VariableStore store = new VariableStore(new User("u"));
		store.setValue("age", 33, false, NOW);
		assertEquals("33", numeric.getStatementLog(store));
	}

	// ---------------------------------------------------------------- //
	// -------------------- time -------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void timeInputParsesGranularityAndBounds() {
		InputTimeCommand time = (InputTimeCommand) inputCommand(4);
		assertEquals(InputCommand.TYPE_TIME, time.getType());
		assertEquals("wake", time.getVariableName());
		assertEquals(15, time.getGranularityMinutes());
		assertNotNull(time.getStartTime());
		assertEquals("07:30", time.getStartTime().evaluate(null));
		assertNotNull(time.getMaxTime());
		assertEquals(InputTimeCommand.TIME_NOW, time.getMaxTime().evaluate(null));
	}

	@Test
	public void timeInputExecuteResolvesTheTimeValues() throws Exception {
		InputTimeCommand time = (InputTimeCommand) inputCommand(4);
		NodeBody processed = new NodeBody();
		time.executeBodyCommand(new LinkedHashMap<>(), processed);

		NodeBody.CommandSegment segment =
				(NodeBody.CommandSegment) processed.getSegments().get(0);
		InputTimeCommand executed = (InputTimeCommand) segment.getCommand();
		assertEquals("07:30", executed.getStartTime().evaluate(null));
		assertEquals(15, executed.getParameters().get("granularityMinutes"));
	}

	@Test
	public void timeInputRejectsAMalformedTimeAttribute() throws Exception {
		List<ParseException> errors = parseErrors("""
			title: Start
			tags:
			speaker: Robot
			---
			[[When: <<input type="time" value="$t" minTime="25:99">>|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			""");
		assertFalse(errors.isEmpty());
		assertTrue(errors.toString(), errors.toString().contains("minTime"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- set --------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void setInputParsesValueOptionPairs() {
		InputSetCommand set = (InputSetCommand) inputCommand(5);
		assertEquals(InputCommand.TYPE_SET, set.getType());
		assertEquals(2, set.getOptions().size());
		assertEquals("dog", set.getOptions().get(0).getVariableName());
		assertEquals("Dog", set.getOptions().get(0).getText().evaluate(null));
		assertEquals("cat", set.getOptions().get(1).getVariableName());
	}

	@Test
	public void setInputParametersAndSelectedStatementLog() {
		InputSetCommand set = (InputSetCommand) inputCommand(5);

		Map<String, ?> params = set.getParameters();
		@SuppressWarnings("unchecked")
		List<Map<String, String>> options = (List<Map<String, String>>) params.get("options");
		assertEquals(2, options.size());
		assertEquals("Dog", options.get(0).get("text"));

		Set<String> writes = new HashSet<>();
		set.getWriteVariableNames(writes);
		assertEquals(Set.of("dog", "cat"), writes);

		VariableStore store = new VariableStore(new User("u"));
		store.setValue("dog", true, false, NOW);
		store.setValue("cat", false, false, NOW);
		assertTrue(set.getStatementLog(store).contains("Dog"));
		assertFalse(set.getStatementLog(store).contains("Cat"));
	}

	@Test
	public void setInputRejectsAValueWithoutAMatchingOption() throws Exception {
		List<ParseException> errors = parseErrors("""
			title: Start
			tags:
			speaker: Robot
			---
			[[Pick: <<input type="set" value1="$a" option1="A" value2="$b">>|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			""");
		assertFalse(errors.isEmpty());
		assertTrue(errors.toString(), errors.toString().contains("option2"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- dispatch / errors -------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void anUnknownInputTypeIsAParseError() throws Exception {
		List<ParseException> errors = parseErrors("""
			title: Start
			tags:
			speaker: Robot
			---
			[[X: <<input type="carrier-pigeon" value="$x">>|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			""");
		assertFalse(errors.isEmpty());
		assertTrue(errors.toString(), errors.toString().contains("type"));
	}

	@Test
	public void aMissingValueAttributeIsAParseError() throws Exception {
		List<ParseException> errors = parseErrors("""
			title: Start
			tags:
			speaker: Robot
			---
			[[X: <<input type="text">>|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			""");
		assertFalse(errors.isEmpty());
		assertTrue(errors.toString(), errors.toString().contains("value"));
	}
}
