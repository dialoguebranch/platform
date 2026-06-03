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
package com.dialoguebranch.model;

public class Constants {

    /** The String constants that defines how individual nodes are separated from each other in the
     * source code of a Dialogue Branch script */
    public static final String DLB_NODE_SEPARATOR = "===";

    /** The String constant that defines how the header is separated from the body in the source
     * code of a Dialogue Branch script */
    public static final String DLB_HEADER_SEPARATOR = "---";

    /** The String constant used as path separator when linking to a different Dialogue Branch
     * script from within the source of a Dialogue Branch script */
    public static final String DLB_PATH_SEPARATOR = "/";

    /** The String constant defining the title of the specific "End" node, which has no contents
     * and defines the end of a conversation */
    public static final String DLB_NODE_END_ID = "end";

    /** The String constant defining the title of the specific "Start" node, which is the default
     * starting point of a conversation */
    public static final String DLB_NODE_START_ID = "start";

    /** The String constant used as the 'statement' part of a reply when that reply is an
     * auto-forward reply */
    public static final String DLB_REPLY_STATEMENT_AUTOFORWARD = "AUTOFORWARD";

    /** The String constant used for (temporarily) naming a dialogue that has not been given a
     * specific name */
    public static final String DLB_DEFAULT_DIALOGUE_NAME = "undefined";

    /** The String constant defining the default language code to use if one has not been defined */
    public static final String DLB_DEFAULT_LANGUAGE_CODE = "en";

    /** The String constant defining the file extension for Dialogue Branch scripts (including the
     * '.') */
    public static final String DLB_SCRIPT_FILE_EXTENSION = ".dlb";

    /** The String constant defining the file extension for Dialogue Branch translation files
     *  (including the '.') */
    public static final String DLB_TRANSLATION_FILE_EXTENSION = ".json";

    /** The list of Strings defining the names of header tags that bear a special meaning within
     * Dialogue Branch */
    public static final String[] DLB_RESERVED_HEADER_TAGS
            = {"title","speaker","position","colorId"};

}
