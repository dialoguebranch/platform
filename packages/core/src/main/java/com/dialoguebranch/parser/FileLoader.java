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

import com.dialoguebranch.model.FileDescriptor;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * A DialogueBranch file loader is used by a {@link ProjectParser} to list and open
 * DialogueBranch dialogue files (.dlb) and translation files (.json) within a DialogueBranch
 * project. The default implementation is {@link ResourceFileLoader}, which can load files from
 * resources on the classpath.
 *
 * @author Dennis Hofs (RRD)
 * @author Harm op den Akker (Fruit Tree Labs - hopdenakker@fruittreelabs.com)
 */
public interface FileLoader {

	/**
	 * Lists all DialogueBranch files in the project. The files should be dialogue files (.dlb) or
	 * translation files (.json).
	 *
	 * @return the List of files as {@link FileDescriptor}s.
	 * @throws IOException if a reading error occurs
	 */
	List<FileDescriptor> listDialogueBranchFiles() throws IOException;

	/**
	 * Opens the specified DialogueBranch file. This should be a dialogue file (.dlb) or a
	 * translation file (.json).
	 *
	 * @param fileDescription the {@link FileDescriptor} object.
	 * @return the {@link Reader} for the file.
	 * @throws IOException if the file cannot be opened.
	 */
	Reader openFile(FileDescriptor fileDescription) throws IOException;
}
