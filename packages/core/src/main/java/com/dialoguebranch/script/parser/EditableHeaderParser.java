/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
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
package com.dialoguebranch.script.parser;

import com.dialoguebranch.script.model.EditableHeader;
import com.dialoguebranch.script.model.EditableNode;
import com.dialoguebranch.script.model.EditableScript;
import com.dialoguebranch.script.warning.ParserWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Scanner;

import static com.dialoguebranch.parser.DialogueBranchParser.NODE_NAME_REGEX;

/**
 * An {@link EditableHeaderParser} may be used to attempt to parse the {@code script} that makes up
 * a given {@link EditableHeader} into a collection of key-value pairs (tags). This is an extremely
 * fault-tolerant parser that is meant to be used at the time of editing scripts.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class EditableHeaderParser {

    Logger logger = LoggerFactory.getLogger(EditableHeaderParser.class);

    /**
     * Perform a fault-tolerant parse on the given {@link EditableHeader}.
     *
     * @param header the {@link EditableHeader} to parse.
     */
    public static void parseHeader(EditableHeader header) {
        // Before starting a new parse, clear all existing warnings in this header
        header.clearWarnings();

        // Also clear all the existing elements in the tags map
        header.getTags().clear();

        // Step 1: Populate the 'tags' of this header from the script
        Scanner scanner = new Scanner(header.getSourceCode());
        int lineNumber = 0;
        while (scanner.hasNextLine()) {

            // Keep track of the line number (within the header) for reporting warnings
            lineNumber++;

            String line = scanner.nextLine();
            line = stripComments(line);

            int sep = line.indexOf(':');
            if (sep == -1) {
                header.addParserWarning(new ParserWarning(
                                lineNumber,"Character : not found in header line."));
            } else {
                String key = line.substring(0, sep).trim();
                String value = line.substring(sep + 1).trim();

                if (key.isEmpty()) {
                    header.addParserWarning(new ParserWarning(
                            lineNumber,"Header tag has empty name."));
                }

                if (header.getTags().containsKey(key)) {
                    header.addParserWarning(new ParserWarning(
                            lineNumber,"Found duplicate header: '" + key + "'."));
                } else {
                    header.getTags().put(key, value);
                }
            }
        }
        scanner.close();

        // Step 2: Analyze the populated tags list for additional problems

        // Title
        String title = header.getTags().get("title");
        if(title == null) {
            header.addParserWarning(new ParserWarning(
                    0,"Missing mandatory 'title' element in header."));
        } else if(title.isEmpty()) {
            header.addParserWarning(new ParserWarning(
                    0,"The mandatory 'title' element is empty."));
        } else {
            if (!title.matches(NODE_NAME_REGEX)) {
                header.addParserWarning(new ParserWarning(
                        0,"The 'title' element contains invalid characters."));
            } else {
                // Check if title exists somewhere else within the script
                EditableScript parentDialogueScript = header.getEditableNode().getEditableScript();
                List<EditableNode> nodes = parentDialogueScript.getNodesByTitle(title);
                if(nodes.size() > 1) {
                    header.addParserWarning(new ParserWarning(
                            0,"Duplicate 'title' element found."));
                }
            }
        }
    }

    private static String stripComments(String line) {
        int commentSeparatorIndex = line.indexOf("//");
        if (commentSeparatorIndex == -1) return line;
        return line.substring(0, commentSeparatorIndex).trim();
    }

}
