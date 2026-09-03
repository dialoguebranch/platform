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

package com.dialoguebranch.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.NodeHeader;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Drives an {@link ActiveDialogue} through its {@code start → progress → reply → finish} lifecycle
 * against a small inline fixture dialogue, plus the reply-statement, reply-input and stateless
 * execution helpers and the error paths. Part of the #90 area-A test hardening (#154).
 */
public class ActiveDialogueTest {

	private static final ZonedDateTime NOW =
			ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneId.of("Europe/Lisbon"));

	/**
	 * Start sets {@code $greeting} and interpolates it. Middle's two replies each set
	 * {@code $choice} via a reply command. Last auto-forwards ({@code [[Wrap]]}, no statement).
	 * Wrap has a plain-text reply that ends the dialogue.
	 */
	private static final String FIXTURE = """
		title: Start
		tags:
		speaker: Robot
		---
		<<set $greeting = "hello">>
		The greeting is $greeting.
		[[Continue.|Middle]]
		[[Quit.|End]]
		===
		title: Middle
		tags:
		speaker: Robot
		---
		Pick one.
		[[Option A.|Last|<<set $choice = "A">>]]
		[[Option B.|Last|<<set $choice = "B">>]]
		===
		title: Last
		tags:
		speaker: Robot
		---
		You chose $choice.
		[[Wrap]]
		===
		title: Wrap
		tags:
		speaker: Robot
		---
		Almost done.
		[[Done.|End]]
		===
		title: End
		tags:
		speaker:
		---

		===
		""";

	private Dialogue dialogue;
	private VariableStore store;
	private ActiveDialogue active;

	@Before
	public void setUp() throws Exception {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("flow", new StringReader(FIXTURE))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			dialogue = result.getDialogue();
		}
		store = new VariableStore(new User("tester"));
		active = new ActiveDialogue(
				new ResourcePointer("en", "flow", ResourceType.SCRIPT), dialogue, store);
	}

	private static int replyId(Node node, int index) {
		List<Reply> replies = node.getBody().getReplies();
		return replies.get(index).getReplyId();
	}

	private Node selectReply(Node current, int index) throws Exception {
		NodePointer pointer = active.processReplyAndGetNodePointer(replyId(current, index), NOW);
		return active.progressDialogue((InternalNodePointer) pointer, NOW);
	}

	// ---------------------------------------------------------------- //
	// -------------------- start ---------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void startReturnsTheStartNodeAndRunsItsSetCommand() throws Exception {
		Node start = active.startDialogue(NOW);

		assertEquals("Start", start.getTitle());
		assertSame(start, active.getCurrentNode());
		assertEquals("hello", store.getValue("greeting"));
		assertEquals(VariableUpdatedSource.DLB_SCRIPT,
				store.getVariable("greeting").getUpdatedSource());
		assertTrue("body should interpolate $greeting: " + start.getBody(),
				start.getBody().toString().contains("hello"));
	}

	@Test
	public void startAtAnExplicitNodeId() throws Exception {
		Node middle = active.startDialogue("Middle", NOW);
		assertEquals("Middle", middle.getTitle());
	}

	@Test
	public void startAtAnUnknownNodeIdThrowsNodeNotFound() {
		ExecutionException ex =
				assertThrows(ExecutionException.class, () -> active.startDialogue("Nope", NOW));
		assertEquals(ExecutionException.Type.NODE_NOT_FOUND, ex.getType());
	}

	@Test
	public void startOnADialogueWithoutAStartNodeThrowsNodeNotFound() {
		Dialogue noStart = new Dialogue("nostart");
		Node other = new Node(new NodeHeader("Other"));
		other.setBody(new NodeBody());
		noStart.addNode(other);
		ActiveDialogue ad = new ActiveDialogue(
				new ResourcePointer("en", "nostart", ResourceType.SCRIPT), noStart, store);

		ExecutionException ex =
				assertThrows(ExecutionException.class, () -> ad.startDialogue(NOW));
		assertEquals(ExecutionException.Type.NODE_NOT_FOUND, ex.getType());
	}

	// ---------------------------------------------------------------- //
	// -------------------- reply + progress ---------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void selectingAReplyRunsItsSetCommandAndReturnsAPointerToTheTarget() throws Exception {
		active.startDialogue("Middle", NOW);

		NodePointer pointer =
				active.processReplyAndGetNodePointer(replyId(active.getCurrentNode(), 0), NOW);

		assertTrue(pointer instanceof InternalNodePointer);
		assertEquals("Last", ((InternalNodePointer) pointer).getTargetNodeId());
		assertEquals("A", store.getValue("choice"));
		assertEquals(VariableUpdatedSource.DLB_SCRIPT,
				store.getVariable("choice").getUpdatedSource());
	}

	@Test
	public void progressAdvancesToAndExecutesTheNextNode() throws Exception {
		active.startDialogue("Middle", NOW);
		Node last = selectReply(active.getCurrentNode(), 1); // Option B -> $choice = "B"

		assertEquals("Last", last.getTitle());
		assertSame(last, active.getCurrentNode());
		assertTrue("Last should interpolate $choice: " + last.getBody(),
				last.getBody().toString().contains("B"));
	}

	@Test
	public void progressToTheEndPointerFinishesTheDialogue() throws Exception {
		Node start = active.startDialogue(NOW);
		Node next = selectReply(start, 1); // "Quit." -> End

		assertNull("a pointer to End yields no next node", next);
		assertNull(active.getCurrentNode());
	}

	@Test
	public void aFullRunFromStartToEnd() throws Exception {
		Node start = active.startDialogue(NOW);
		Node middle = selectReply(start, 0);          // Continue. -> Middle
		assertEquals("Middle", middle.getTitle());
		Node last = selectReply(middle, 0);            // Option A. -> Last
		assertEquals("Last", last.getTitle());
		Node wrap = selectReply(last, 0);              // [[Wrap]] auto-forward
		assertEquals("Wrap", wrap.getTitle());
		Node end = selectReply(wrap, 0);               // Done. -> End
		assertNull(end);
	}

	// ---------------------------------------------------------------- //
	// -------------------- reply statement text ------------------ //
	// ---------------------------------------------------------------- //

	@Test
	public void userStatementFromReplyIdReturnsThePlainReplyText() throws Exception {
		active.startDialogue("Wrap", NOW);
		String statement =
				active.getUserStatementFromReplyId(replyId(active.getCurrentNode(), 0));
		assertEquals("Done.", statement);
	}

	@Test
	public void userStatementForAnAutoForwardReplyIsTheAutoForwardConstant() throws Exception {
		active.startDialogue("Last", NOW);
		String statement =
				active.getUserStatementFromReplyId(replyId(active.getCurrentNode(), 0));
		assertEquals(DialogueBranchConstants.DLB_REPLY_STATEMENT_AUTOFORWARD, statement);
	}

	@Test
	public void userStatementForAnUnknownReplyIdThrowsReplyNotFound() throws Exception {
		active.startDialogue("Wrap", NOW);
		ExecutionException ex = assertThrows(ExecutionException.class,
				() -> active.getUserStatementFromReplyId(9999));
		assertEquals(ExecutionException.Type.REPLY_NOT_FOUND, ex.getType());
	}

	// ---------------------------------------------------------------- //
	// -------------------- reply input + stateless -------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void storeReplyInputWritesValuesTaggedInputReply() throws Exception {
		active.startDialogue(NOW);
		active.storeReplyInput(Map.of("age", 30), NOW);

		assertEquals(30, store.getValue("age"));
		assertEquals(VariableUpdatedSource.INPUT_REPLY,
				store.getVariable("age").getUpdatedSource());
	}

	@Test
	public void executeNodeStatelessResolvesTextButDoesNotWriteVariables() throws Exception {
		Node rawStart = dialogue.getNodeById("Start");
		Node executed = active.executeNodeStateless(rawStart, NOW);

		assertTrue(executed.getBody().toString().contains("hello"));
		assertNull("<<set>> must have no effect in stateless execution",
				store.getVariable("greeting"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- accessors ----------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void accessorsAndVariableStoreRepointing() {
		assertSame(dialogue, active.getDialogueDefinition());
		assertEquals("flow", active.getDialogueFileDescription().getDialogueName());

		Node marker = new Node(new NodeHeader("Marker"));
		active.setCurrentNode(marker);
		assertSame(marker, active.getCurrentNode());

		assertSame(store, active.getVariableStore());
		VariableStore replacement = new VariableStore(new User("other"));
		active.setVariableStore(replacement);
		assertSame(replacement, active.getVariableStore());
	}
}
