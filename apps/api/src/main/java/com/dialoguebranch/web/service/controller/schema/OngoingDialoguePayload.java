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

import com.dialoguebranch.web.service.controller.DialogueController;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An {@link OngoingDialoguePayload} object contains information about an ongoing dialogue in the
 * Dialogue Branch Web Service, as provided by the
 * {@link DialogueController} /dialogue/get-ongoing
 * end-point.
 *
 * @author Harm op den Akker
 */
public class OngoingDialoguePayload {

	@Schema(description = "The name of the latest ongoing dialogue (not finished, or cancelled).",
			example = "dialogue-name")
	private String dialogueName;

	@Schema(description = "The unique identifier of the ongoing logged dialogue.",
			example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
	private String loggedDialogueId;

	@Schema(description = "How many seconds ago was the latest engagement with this dialogue.",
			example = "60")
	private long secondsSinceLastEngagement;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an empty instance of an {@link OngoingDialoguePayload}.
	 */
	public OngoingDialoguePayload() { }

	/**
	 * Creates an instance of a {@link OngoingDialoguePayload} object with a given
	 * {@code dialogueName} and {@code secondsSinceLastEngagement}.
	 * @param dialogueName the name of the dialogue.
	 * @param secondsSinceLastEngagement the number of seconds since the user last engaged with this
	 *                                   dialogue.
	 */
	public OngoingDialoguePayload(String dialogueName, String loggedDialogueId,
								  long secondsSinceLastEngagement) {
		this.dialogueName = dialogueName;
		this.loggedDialogueId = loggedDialogueId;
		this.secondsSinceLastEngagement = secondsSinceLastEngagement;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of the dialogue.
	 * @return the name of the dialogue.
	 */
	public String getDialogueName() {
		return dialogueName;
	}

	/**
	 * Sets the name of the dialogue.
	 * @param dialogueName the name of the dialogue.
	 */
	public void setDialogueName(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	/**
	 * Returns the unique identifier of the ongoing logged dialogue.
	 * @return the unique identifier of the ongoing logged dialogue.
	 */
	public String getLoggedDialogueId() {
		return loggedDialogueId;
	}

	/**
	 * Sets the unique identifier of the ongoing logged dialogue.
	 * @param loggedDialogueId the unique identifier of the ongoing logged dialogue.
	 */
	public void setLoggedDialogueId(String loggedDialogueId) {
		this.loggedDialogueId = loggedDialogueId;
	}

	/**
	 * Returns the number of seconds since the user last engaged with this dialogue.
	 * @return the number of seconds since the user last engaged with this dialogue.
	 */
	public long getSecondsSinceLastEngagement() {
		return secondsSinceLastEngagement;
	}

	/**
	 * Sets the number of seconds since the user last engaged with this dialogue.
	 * @param secondsSinceLastEngagement the number of seconds since the user last engaged with this
	 *                                   dialogue.
	 */
	public void setSecondsSinceLastEngagement(long secondsSinceLastEngagement) {
		this.secondsSinceLastEngagement = secondsSinceLastEngagement;
	}

}
