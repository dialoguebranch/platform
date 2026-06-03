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

package com.dialoguebranch.parser;

import nl.rrd.utils.exception.ParseException;
import com.dialoguebranch.model.Dialogue;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ParserResult} object contains the results of parsing a .dlb file, including the
 * resulting {@link Dialogue} and a list of {@link ParseException}s.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ParserResult {

    private Dialogue dialogue;
    private final List<ParseException> parseErrors;

    /**
     * Creates an instance of an empty {@link ParserResult} object.
     */
    public ParserResult() {
        parseErrors = new ArrayList<>();
    }

    /**
     * Returns the {@link Dialogue} that is part of this {@link ParserResult}.
     * @return the {@link Dialogue} that is part of this {@link ParserResult}.
     */
    public Dialogue getDialogue() {
        return dialogue;
    }

    /**
     * Returns a {@link List} of {@link ParseException}s that have occurred during the parsing of
     * the .dlb file.
     * @return a {@link List} of {@link ParseException}s.
     */
    public List<ParseException> getParseErrors() {
        return parseErrors;
    }

    /**
     * Sets the {@link Dialogue} that is part of this {@link ParserResult}.
     * @param dialogue the {@link Dialogue} that is part of this {@link ParserResult}.
     */
    public void setDialogue(Dialogue dialogue) {
        this.dialogue = dialogue;
    }

}
