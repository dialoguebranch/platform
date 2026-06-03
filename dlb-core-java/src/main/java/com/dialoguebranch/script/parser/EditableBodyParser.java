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

import com.dialoguebranch.script.model.EditableBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditableBodyParser {

    Logger logger = LoggerFactory.getLogger(EditableBodyParser.class);

    /**
     * Perform a fault-tolerant parse on the given {@link EditableBody}.
     *
     * @param body the {@link EditableBody} to parse.
     */
    public static void parseBody(EditableBody body) {

        // Before starting a new parse, clear all existing warnings in this body
        body.clearWarnings();

        String script = body.getSourceCode();

        // Extract everything that looks like a reply option (i.e. [[...|...]] )
        List<String> replyOptions = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[\\[.*?\\]\\]");
        Matcher matcher = pattern.matcher(script);
        while(matcher.find()) {
            String replyOption = script.substring(matcher.start(),matcher.end());
            replyOptions.add(replyOption);
        }

        for(String replyOption : replyOptions) {
            String replyOptionContent = replyOption.substring(2,replyOption.length()-2);
            String[] replyTokens = replyOptionContent.split("\\|");

            // Extract the ID of the node that this reply points to
            String nodePointerString = "";
            if(replyTokens.length == 1) {
                nodePointerString = replyTokens[0];
            } else if(replyTokens.length > 1) {
                nodePointerString = replyTokens[1];
            }

            // TODO: Fix this after cleaning up the NodePointer implementation

            //if(nodePointerString.contains(".")) {
            //String[] referenceTokens = nodePointerString.split("\\.");
            //    ExternalNodePointer pointer = new ExternalNodePointer(getTitle(),referenceTokens[1],referenceTokens[0]);
            //    addReferencedNode(pointer);
            //} else {
            //    InternalNodePointer pointer = new InternalNodePointer(getTitle(),referenceString);
            //    addReferencedNode(pointer);
            //}
        }

    }

}
