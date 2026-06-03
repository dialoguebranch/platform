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

import com.dialoguebranch.exception.ScriptParseException;
import com.dialoguebranch.model.Constants;
import com.dialoguebranch.script.model.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link EditableScriptParser} may be used to create an {@link EditableScript} object
 * from a given {@link File} and {@code languageCode}. This parser is meant to be used by tools that
 * provide editing functionality, and as such it is extremely fault-tolerant. Basically, any file
 * with the correct extension will result in a successful parse, and it is up to the user to decide
 * what to do with the results.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class EditableScriptParser {

    /**
     * Attempts to create a {@link EditableScript} from the given {@link File} and with the
     * {@code languageCode} provided. The file must have the correct file extension, as defined in
     * {@link Constants#DLB_SCRIPT_FILE_EXTENSION}.
     *
     * <p>This reader will split the given input into {@link EditableNode}s based on the {@link
     * Constants#DLB_NODE_SEPARATOR}, but otherwise does not parse these individual nodes. As such,
     * any given file should successfully parse, as long as its file extension is correct.</p>
     *
     * @param file the Dialogue Branch script file to parse.
     * @param languageCode the language code for the {@link EditableScript} to generate.
     * @return a {@link EditableScript} object containing the contents of the script file.
     * @throws IOException in case of a read error.
     * @throws ScriptParseException in case the file has the wrong extension.
     */
    public static EditableScript read(File file, String languageCode)
            throws IOException, ScriptParseException {

        // Instantiate the EditableScript object based on the given file
        EditableScript editableScript = getDialogueBranchScript(file, languageCode);

        List<String> linesBuffer = new ArrayList<>();

        // Read through the file line-by-line
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                linesBuffer.add(line);
            }
        }

        if(!linesBuffer.isEmpty()) {
            setContents(linesBuffer, editableScript);
        }

        return editableScript;
    }

    public static void setContents(List<String> lines, EditableScript editableScript) {
        List<String> linesBuffer = new ArrayList<>();

        for(String line : lines) {

            // When we encounter the NODE_SEPARATOR, we take what we have and create a new node
            if(line.equals(Constants.DLB_NODE_SEPARATOR)) {
                editableScript.addNode(createNode(editableScript, linesBuffer));
                linesBuffer = new ArrayList<>();
            } else {
                // Otherwise, we add the line to our line buffer
                linesBuffer.add(line);
            }

        }

        if(!linesBuffer.isEmpty()) {
            // If we have some leftover stuff, try to make a node out of it
            // This can happen if the last node is not ended with a node separator
            // But we don't want to discard this data
            editableScript.addNode(createNode(editableScript, linesBuffer));
        }
    }

    /**
     * Create a {@link EditableNode} from a list of lines. If there is a header separator, it will
     * create a node with a separate header and body.
     *
     * @param editableScript the {@link EditableScript} that should be the parent of the created
     *                       nodes.
     * @param lines the list of lines to convert into a {@link EditableNode}.
     * @return a {@link EditableNode}, or {@code null} if the given list of Strings is empty.
     */
    private static EditableNode createNode(EditableScript editableScript, List<String> lines) {

        EditableNode createdNode = new EditableNode(editableScript);

        // If there is no lines to work with, return null
        if(lines.isEmpty()) {
            return null;
        }

        // If the only line we have is a header separator, return null
        if(lines.size() == 1 && lines.contains(Constants.DLB_HEADER_SEPARATOR)) {
            return null;
        }

        // If there is no header separator, everything is considered body
        else if(!lines.contains(Constants.DLB_HEADER_SEPARATOR)) {
            EditableBody body = new EditableBody(createdNode,lines);
            createdNode.setBody(body);
            return createdNode;
        }

        // If the header separator is the last element of the list, everything is header
        else if(lines.indexOf(Constants.DLB_HEADER_SEPARATOR) == lines.size()-1) {
            EditableHeader header = new EditableHeader(createdNode,lines);
            createdNode.setHeader(header);
            return createdNode;
        }

        // Else, split the lines into header and body
        else {
            EditableHeader header = new EditableHeader(createdNode,
                    lines.subList(0, lines.indexOf(Constants.DLB_HEADER_SEPARATOR)));
            EditableBody body = new EditableBody(createdNode,
                    lines.subList(lines.indexOf(Constants.DLB_HEADER_SEPARATOR)+1, lines.size()));

            createdNode.setBody(body);
            createdNode.setHeader(header);
            return createdNode;
        }

    }

    /**
     * Creates a {@link EditableScript} object with the name of the given file, if the
     * extension of the given file is correct (see {@link Constants#DLB_SCRIPT_FILE_EXTENSION}).
     *
     * @param file the File from which to create the {@link EditableScript}.
     * @return a {@link EditableScript} object with the name of the given file.
     * @throws ScriptParseException in case of an incorrect file extension.
     */
    private static EditableScript getDialogueBranchScript(File file, String languageCode)
            throws ScriptParseException {
        String extension = file.getName().substring(file.getName().lastIndexOf("."));

        if(!extension.equals(Constants.DLB_SCRIPT_FILE_EXTENSION))
            throw new ScriptParseException(
                    "Invalid file extension '" + extension + "', expected: '"
                            + Constants.DLB_SCRIPT_FILE_EXTENSION);

        // Create a new EditableScript with the file name as dialogueName
        String fileName = file.getName().substring(0, file.getName().lastIndexOf("."));
        return new EditableScript(fileName, languageCode, new FileStorageSource(file));
    }

}
