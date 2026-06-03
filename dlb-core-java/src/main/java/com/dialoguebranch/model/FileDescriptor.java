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

import java.io.File;
import java.util.Objects;

/**
 * A {@link FileDescriptor} contains metadata for a Dialogue Branch file that can either be a
 * Script file (.dlb) or a Translation file (.json). It has three properties:
 * <ul>
 *   <li>{@code language} - the name of the language folder in which the file was found</li>
 *
 *   <li>{@code filePath} - the complete path to the file, relative to the language folder,
 *   including the file extension</li>
 *
 *   <li>{@code resourceType} - either {@link ResourceType#SCRIPT} or {@link ResourceType#TRANSLATION}
 *   indicating the type of the file</li>
 * </ul>
 *
 * Additionally, one can obtain the unique "dialogue identifier" by calling
 * {@link FileDescriptor#getDialogueName()} which will return the name of the dialogue without
 * its file extension (but including any subdirectories under the language directory in which it
 * resides).
 *
 * @author Harm op den Akker (Fruit Tree Labs).
 */
public class FileDescriptor {
	
	private String language;
	private String filePath;
	private ResourceType resourceType;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an empty instance of a {@link FileDescriptor}.
	 */
	public FileDescriptor() {	}

	/**
	 * Creates an instance of a {@link FileDescriptor} with a given {@code language}, {@code
	 * filePath} and {@code resourceType}.
	 *
	 * @param language the name of the "language directory", which is the direct subdirectory of the
	 *                 project's root directory (e.g. "en" - for English).
	 * @param filePath the path to the file, relative to the language directory and including the
	 *                 file extension (e.g. "subdirectory/basic.dlb").
	 * @param resourceType the type of the file as either {@link ResourceType#SCRIPT} or
	 *                 {@link ResourceType#TRANSLATION}.
	 */
	public FileDescriptor(String language, String filePath, ResourceType resourceType) {
		this.setLanguage(language);
		this.setFilePath(filePath);
		this.resourceType = resourceType;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the language of this {@link FileDescriptor}, which is the name of the "language
	 * directory", which is the direct subdirectory of the project's root directory (e.g. "en" - for
	 * English).
	 *
	 * @return the language of this {@link FileDescriptor}.
	 */
	public String getLanguage() {
		return this.language;
	}

	/**
	 * Sets the language of this {@link FileDescriptor}, which is the name of the "language
	 * directory", which is the direct subdirectory of the project's root directory (e.g. "en" - for
	 * English).
	 *
	 * @param language the language of this {@link FileDescriptor}.
	 */
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * Returns the file path of this {@link FileDescriptor} which is the path to the file,
	 * relative to the language directory and including the file extension
	 * (e.g. "subdirectory/basic.dlb").
	 *
	 * @return the file path of this {@link FileDescriptor}.
	 */
	public String getFilePath() {
		return this.filePath;
	}

	/**
	 * Sets the file path of this {@link FileDescriptor} which is the path to the file,
	 * relative to the language directory and including the file extension
	 * (e.g. "subdirectory/basic.dlb").
	 *
	 * @param filePath the file path of this {@link FileDescriptor}.
	 */
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	/**
	 * Returns the type of this {@link FileDescriptor} as either {@link ResourceType#SCRIPT} or
	 * {@link ResourceType#TRANSLATION}.
	 *
	 * @return the type of this {@link FileDescriptor}.
	 */
	public ResourceType getFileType() {
		return resourceType;
	}

	/**
	 * Sets the type of this {@link FileDescriptor} as either {@link ResourceType#SCRIPT} or
	 * {@link ResourceType#TRANSLATION}.
	 *
	 * @param resourceType the type of this {@link FileDescriptor}.
	 */
	public void setFileType(ResourceType resourceType) {
		this.resourceType = resourceType;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	/**
	 * Returns the "Dialogue Name" associated with this {@link FileDescriptor}, which is the
	 * relative path of the dialogue file (relative to the language folder), including the file
	 * name, without the extension. For example, for a .dlb script file located at /project-folder/
	 * en/sub-folder/script.dlb, this method will return "sub-folder/script".
	 *
	 * @return the uniquely identifying dialogue name.
	 */
	public String getDialogueName() {
		if (filePath.endsWith(Constants.DLB_SCRIPT_FILE_EXTENSION)) {
			return filePath.substring(0,filePath.length() -
					Constants.DLB_SCRIPT_FILE_EXTENSION.length());
		} else if(filePath.endsWith(Constants.DLB_TRANSLATION_FILE_EXTENSION)) {
			return filePath.substring(0, filePath.length() -
					Constants.DLB_TRANSLATION_FILE_EXTENSION.length());
		}
		else
			return filePath;
	}

	@Override
	public int hashCode() {
		return Objects.hash(language, filePath, resourceType);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj == null || obj.getClass() != getClass())
			return false;
		FileDescriptor other = (FileDescriptor)obj;
		if (!language.equals(other.language))
			return false;
		if (!filePath.equals(other.filePath))
			return false;
		return resourceType.equals(other.resourceType);
	}

	@Override
	public String toString() {
		return "DialogueBranch File '" + this.getDialogueName() + "' in language '"
			+ this.getLanguage() + "' (" + this.getLanguage() + File.separator
			+ this.getFilePath() +").";
	}

}
