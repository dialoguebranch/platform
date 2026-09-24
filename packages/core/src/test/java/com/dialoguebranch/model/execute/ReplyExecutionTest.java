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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.execute.command.ActionCommand;
import com.dialoguebranch.model.execute.command.Command;
import com.dialoguebranch.model.execute.command.SetCommand;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Characterizes {@link Reply#execute}'s command resolution (#293, a prerequisite for #210's
 * immutable-model refactor): command count and order are preserved, an {@link ActionCommand}
 * resolves to a new instance with its variables substituted, and a {@link SetCommand} (or any
 * other non-{@code ActionCommand}) is carried over unresolved — {@code Reply.execute()} only ever
 * special-cases {@code ActionCommand}, since a reply's {@code <<set>>} only actually runs once the
 * reply is chosen, not while the node is being displayed.
 *
 * @author Harm op den Akker
 */
public class ReplyExecutionTest {

	private static Reply firstReplyOf(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("replyexec", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue().getNodeById("Start").getBody().getReplies().get(0);
		}
	}

	@Test
	public void statementVariablesAreResolved() throws Exception {
		Reply reply = firstReplyOf("""
			title: Start
			tags:
			speaker: Robot
			---
			Hello.

			[[Hi, $name.|Start]]
			===
			""");

		Reply executed = reply.execute(Map.of("name", "Robin"));

		assertEquals("Hi, Robin.", executed.getStatement().toString());
	}

	@Test
	public void actionCommandResolvesToANewInstanceWithVariablesSubstituted() throws Exception {
		Reply reply = firstReplyOf("""
			title: Start
			tags:
			speaker: Robot
			---
			Hello.

			[[Continue.|Start|<<action type="generic" value="$thing">>]]
			===
			""");
		ActionCommand original = (ActionCommand) reply.getCommands().get(0);

		Reply executed = reply.execute(Map.of("thing", "widget"));

		ActionCommand resolved = (ActionCommand) executed.getCommands().get(0);
		assertNotSame("Expected a new instance, not the original mutated in place",
				original, resolved);
		assertTrue(resolved.toString(), resolved.toString().contains("widget"));
	}

	@Test
	public void nonActionCommandsAreCarriedOverUnresolved() throws Exception {
		Reply reply = firstReplyOf("""
			title: Start
			tags:
			speaker: Robot
			---
			Hello.

			[[Continue.|Start|<<set $x = 1>>]]
			===
			""");
		Command original = reply.getCommands().get(0);

		Reply executed = reply.execute(Map.of());

		assertSame("A non-ActionCommand should be carried over as-is, not replaced",
				original, executed.getCommands().get(0));
	}

	@Test
	public void commandCountAndOrderArePreserved() throws Exception {
		Reply reply = firstReplyOf("""
			title: Start
			tags:
			speaker: Robot
			---
			Hello.

			[[Continue.|Start|<<set $x = 1>><<action type="generic" value="first">><<set $y = 2>>\
			<<action type="generic" value="second">>]]
			===
			""");

		Reply executed = reply.execute(Map.of());

		List<Command> commands = executed.getCommands();
		assertEquals(4, commands.size());
		assertTrue(commands.get(0) instanceof SetCommand);
		assertTrue(commands.get(1) instanceof ActionCommand);
		assertTrue(((ActionCommand) commands.get(1)).toString().contains("first"));
		assertTrue(commands.get(2) instanceof SetCommand);
		assertTrue(commands.get(3) instanceof ActionCommand);
		assertTrue(((ActionCommand) commands.get(3)).toString().contains("second"));
	}
}
