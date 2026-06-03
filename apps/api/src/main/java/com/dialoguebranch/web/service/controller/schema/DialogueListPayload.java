/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.service.controller.schema;

import io.swagger.v3.oas.annotations.media.Schema;
import nl.rrd.utils.json.JsonObject;

import java.util.Arrays;

/**
 * A {@link DialogueListPayload} object contains a list of dialogue names that is somehow provided
 * by the web service.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class DialogueListPayload extends JsonObject {

    @Schema(description = "A list of dialogue names, supported by the web service",
            example = "[dialogue1,dialogue2]")
    private String[] dialogueNames;

    // --------------------------------------------------------
    // -------------------- Constructor(s) --------------------
    // --------------------------------------------------------

    /**
     * Creates an empty instance of a {@link DialogueListPayload} object.
     */
    public DialogueListPayload() { }

    /**
     * Creates an instance of a {@link DialogueListPayload} object with a given array of {@code
     * dialogueNames}.
     *
     * @param dialogueNames an array of dialogue names as a {@code String[]}.
     */
    public DialogueListPayload(String[] dialogueNames) {
        this.dialogueNames = sortDialogueNames(dialogueNames);
    }

    // -----------------------------------------------------------
    // -------------------- Getters & Setters --------------------
    // -----------------------------------------------------------

    /**
     * Returns an array of dialogue names in this {@link DialogueListPayload}.
     * @return an array of dialogue names in this {@link DialogueListPayload}.
     */
    public String[] getDialogueNames() {
        return dialogueNames;
    }

    /**
     * Sets the array of dialogue names in this {@link DialogueListPayload}.
     * @param dialogueNames the array of dialogue names in this {@link DialogueListPayload}.
     */
    public void setDialogueNames(String[] dialogueNames) {
        this.dialogueNames = sortDialogueNames(dialogueNames);
    }

    /**
     * Takes a String array of dialogue names, and returns the sorted list.
     * TODO: Make sure the sorting takes into account the expected ordering of "folders first"
     * @param dialogueNames the list of dialogue names to sort.
     * @return a sorted list of dialogues.
     */
    private String[] sortDialogueNames(String[] dialogueNames) {
        Arrays.sort(dialogueNames);
        return dialogueNames;
    }

}
