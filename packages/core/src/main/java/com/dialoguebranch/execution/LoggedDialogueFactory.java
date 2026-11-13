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

import com.dialoguebranch.model.execute.LoggedDialogue;

import java.time.ZonedDateTime;

/**
 * Creates the log of a dialogue that a conversation enters when a reply leads into another dialogue
 * (#483). An {@link ActiveDialogue} that keeps a log asks for a new one at every such switch, so the
 * application decides what the log is: its identifier, and what it records besides what {@link
 * LoggedDialogue} holds, such as the session the dialogues belong to.
 */
@FunctionalInterface
public interface LoggedDialogueFactory {

	/**
	 * Creates the log of the dialogue that is entered.
	 *
	 * @param previous the log of the dialogue the reply was given in, to take the user and session
	 *                 from. It is completed once the first step of the new dialogue has run.
	 * @param entered the dialogue that is entered.
	 * @param eventTime the time of the reply that leads into it.
	 * @return a new, empty log for the entered dialogue.
	 */
	LoggedDialogue create(LoggedDialogue previous, ResolvedDialogue entered, ZonedDateTime eventTime);
}
