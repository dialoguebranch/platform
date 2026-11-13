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

package com.dialoguebranch.model.execute.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Pins down the exact JSON of a {@link DialogueMessage}: what the Web Service sends for every
 * dialogue step, and what Studio and client-js read. Helper methods on the protocol types, such
 * as {@link DialogueStatement#getText()}, must not add fields to it (#485).
 */
public class DialogueMessageJsonTest {

	/** A step with every kind of segment and reply: text, input, action, auto-forward, End. */
	static DialogueMessage fullMessage() {
		DialogueStatement statement = new DialogueStatement();
		statement.getSegments().add(new DialogueStatement.TextSegment("Hello, "));
		statement.getSegments().add(new DialogueStatement.ActionSegment(
				new DialogueAction("image", "wave.png", Map.of())));
		statement.getSegments().add(new DialogueStatement.TextSegment("Sam."));

		DialogueStatement answer = new DialogueStatement();
		answer.getSegments().add(new DialogueStatement.TextSegment("I am "));
		answer.getSegments().add(new DialogueStatement.InputSegment("numeric", "age",
				Map.of("variable", "age")));
		answer.getSegments().add(new DialogueStatement.TextSegment(" years old."));
		ReplyMessage withInput = new ReplyMessage();
		withInput.setReplyId(1);
		withInput.setStatement(answer);

		ReplyMessage autoForward = new ReplyMessage();
		autoForward.setReplyId(2);
		autoForward.addAction(new DialogueAction("generic", "nod", Map.of()));

		DialogueStatement bye = new DialogueStatement();
		bye.addTextSegment("Bye.");
		ReplyMessage ending = new ReplyMessage();
		ending.setReplyId(3);
		ending.setStatement(bye);
		ending.setEndsDialogue(true);

		return new DialogueMessage("intro/welcome", "Start", "log-1", 4, "Robin", statement,
				new ArrayList<>(List.of(withInput, autoForward, ending)));
	}

	/** The JSON of {@link #fullMessage()}, as the Web Service has always sent it. */
	private static final String EXPECTED = """
			{"dialogue": "intro/welcome", "node": "Start",
			 "loggedDialogueId": "log-1", "loggedInteractionIndex": 4, "speaker": "Robin",
			 "statement": {"segments": [
			   {"text": "Hello, ", "segmentType": "TEXT"},
			   {"action": {"type": "image", "value": "wave.png", "parameters": {}},
			    "segmentType": "ACTION"},
			   {"text": "Sam.", "segmentType": "TEXT"}]},
			 "replies": [
			   {"replyId": 1,
			    "statement": {"segments": [
			      {"text": "I am ", "segmentType": "TEXT"},
			      {"segmentType": "INPUT", "inputType": "numeric", "description": "age",
			       "variable": "age"},
			      {"text": " years old.", "segmentType": "TEXT"}]},
			    "actions": [], "endsDialogue": false},
			   {"replyId": 2, "statement": null,
			    "actions": [{"type": "generic", "value": "nod", "parameters": {}}],
			    "endsDialogue": false},
			   {"replyId": 3,
			    "statement": {"segments": [{"text": "Bye.", "segmentType": "TEXT"}]},
			    "actions": [], "endsDialogue": true}]}
			""";

	@Test
	public void theJsonOfAStepIsUnchanged() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(fullMessage());
		assertEquals(json, mapper.readTree(EXPECTED), mapper.readTree(json));
	}
}
