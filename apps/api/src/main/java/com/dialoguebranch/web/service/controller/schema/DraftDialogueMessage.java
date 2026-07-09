/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.web.service.controller.schema;

import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.web.service.controller.DraftExecutionController;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload for {@link DraftExecutionController} /draft/start, pairing the ephemeral
 * {@code draftSessionId} (to be passed to subsequent /draft/progress, /draft/cancel, or
 * /draft/revert-variables calls) with the usual {@link DialogueMessage} for the current node.
 *
 * @author Harm op den Akker
 */
public class DraftDialogueMessage {

	@Schema(description = "The identifier of the ephemeral draft test session, to be passed to " +
			"subsequent /draft/progress, /draft/cancel, or /draft/revert-variables calls.",
			example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
	private String draftSessionId;

	@Schema(description = "The dialogue message for the current node.")
	private DialogueMessage dialogueMessage;

	/**
	 * Creates an empty instance of a {@link DraftDialogueMessage}.
	 */
	public DraftDialogueMessage() { }

	/**
	 * Creates an instance of a {@link DraftDialogueMessage} with the given
	 * {@code draftSessionId} and {@code dialogueMessage}.
	 *
	 * @param draftSessionId  the identifier of the ephemeral draft test session.
	 * @param dialogueMessage the dialogue message for the current node.
	 */
	public DraftDialogueMessage(String draftSessionId, DialogueMessage dialogueMessage) {
		this.draftSessionId = draftSessionId;
		this.dialogueMessage = dialogueMessage;
	}

	/**
	 * Returns the identifier of the ephemeral draft test session.
	 *
	 * @return the draft session identifier.
	 */
	public String getDraftSessionId() {
		return draftSessionId;
	}

	/**
	 * Sets the identifier of the ephemeral draft test session.
	 *
	 * @param draftSessionId the draft session identifier.
	 */
	public void setDraftSessionId(String draftSessionId) {
		this.draftSessionId = draftSessionId;
	}

	/**
	 * Returns the dialogue message for the current node.
	 *
	 * @return the dialogue message.
	 */
	public DialogueMessage getDialogueMessage() {
		return dialogueMessage;
	}

	/**
	 * Sets the dialogue message for the current node.
	 *
	 * @param dialogueMessage the dialogue message.
	 */
	public void setDialogueMessage(DialogueMessage dialogueMessage) {
		this.dialogueMessage = dialogueMessage;
	}

}
