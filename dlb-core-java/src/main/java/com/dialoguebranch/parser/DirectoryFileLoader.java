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

import com.dialoguebranch.model.Constants;
import com.dialoguebranch.model.FileDescriptor;
import com.dialoguebranch.model.ResourceType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of a {@link FileLoader} that can generate a list of
 * {@link FileDescriptor}s by finding all .dlb and .json files in a given directory.
 * The directory provided when creating this {@link DirectoryFileLoader} is assumed to have one
 * or many subdirectories, representing different languages, that contain .dlb and/or .json files.
 * For example:
 * <br/>
 * <ul>
 *   <li>/directory/</li>
 *   <li>
 *     <ul>
 *       <li>en/</li>
 *       <li>
 *         <ul>
 *           <li>script1.dlb</li>
 *           <li>script2.dlb</li>
 *           <li>...</li>
 *         </ul>
 *       </li>
 *       <li>pt/</li>
 *       <li>
 *         <ul>
 *           <li>script1.json</li>
 *           <li>script2.json</li>
 *           <li>...</li>
 *         </ul>
 *      </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 *
 * @param rootDirectory the root directory for this {@link DirectoryFileLoader}.
 */
public record DirectoryFileLoader(File rootDirectory) implements FileLoader {

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the root directory for this {@link DirectoryFileLoader}.
	 *
	 * @return the root directory for this {@link DirectoryFileLoader}.
	 */
	@Override
	public File rootDirectory() {
		return rootDirectory;
	}

	// -------------------------------------------------------------------
	// -------------------- Interface Implementations --------------------
	// -------------------------------------------------------------------

	@Override
	public List<FileDescriptor> listDialogueBranchFiles() {
		List<FileDescriptor> result = new ArrayList<>();
		File[] children = rootDirectory.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!child.isDirectory() || child.getName().startsWith("."))
					continue;
				String language = child.getName();
				result.addAll(listDir(language, "", child));
			}
		}
		return result;
	}

	@Override
	public Reader openFile(FileDescriptor fileDescription) throws IOException {
		File file = new File(rootDirectory, fileDescription.getLanguage() + File.separator +
				fileDescription.getFilePath());
		return new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------
	// -------------------- Other Functions --------------------
	// ---------------------------------------------------------

	/**
	 * Recursively generates a list of {@link FileDescriptor} objects from all .dlb and/or .json
	 * files in the given {@code directory} (and all its subdirectories), under the given relative
	 * {@code pathName} (relative to the {@code rootDirectory} of this {@link DirectoryFileLoader}).
	 * Each {@link FileDescriptor} will have its language attribute set to the given {@code
	 * language} parameter, which is the direct sub-folder of the {@code rootDirectory} under which
	 * it was found.
	 *
	 * @param language the language code, or name of the main folder.
	 * @param pathName the relative pathName in which the given {@code directory} can be found.
	 * @param directory the directory in which to look for .dlb and .json files.
	 * @return a list of all encountered .dlb and .json files as {@code FileDescriptor}s.
	 */
	private List<FileDescriptor> listDir(String language, String pathName, File directory) {
		List<FileDescriptor> result = new ArrayList<>();
		File[] children = directory.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isDirectory() && !child.getName().startsWith(".")) {
					result.addAll(listDir(language, pathName + child.getName()
							+ "/", child));
				} else if (child.isFile()) {
					if (child.getName().endsWith(Constants.DLB_SCRIPT_FILE_EXTENSION)) {
						result.add(new FileDescriptor(
								language,
								pathName + child.getName(),
								ResourceType.SCRIPT));
					} else if (child.getName().endsWith(Constants.DLB_TRANSLATION_FILE_EXTENSION)) {
						result.add(new FileDescriptor(
								language,
								pathName + child.getName(),
								ResourceType.TRANSLATION));
					}
				}
			}
		}
		return result;
	}

}
