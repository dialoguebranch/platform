/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.model.common.ResourceType;

import java.util.Objects;

/**
 * A {@link ResourcePointer} contains metadata for a Dialogue Branch resource that can either be a
 * Script (i.e. {@link ResourceType#SCRIPT}) or a Translation (e.g. {@link ResourceType#TRANSLATION}).
 * It has three properties:
 * <ul>
 *   <li>{@code language} - the name of the language to which this resource belongs</li>
 *
 *   <li>{@code dialogueName} - the storage-agnostic identifier for the dialogue, relative to the
 *   language folder and without any file extension (e.g. "subfolder/basic")</li>
 *
 *   <li>{@code resourceType} - either {@link ResourceType#SCRIPT} or {@link ResourceType#TRANSLATION}
 *   indicating the type of the resource</li>
 * </ul>
 *
 * The {@code dialogueName} uniquely identifies a dialogue within a language regardless of the
 * underlying storage system (file system, database, etc.). For file-based storage, callers are
 * responsible for appending the appropriate extension when constructing the actual file path.
 *
 * @author Harm op den Akker
 */
public class ResourcePointer {
	
	private String language;
	private String dialogueName;
	private ResourceType resourceType;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an empty instance of a {@link ResourcePointer}.
	 */
	public ResourcePointer() {	}

	/**
	 * Creates an instance of a {@link ResourcePointer} with a given {@code language}, {@code
	 * dialogueName} and {@code resourceType}.
	 *
	 * @param language the name of the language to which this Resource belongs.
	 * @param dialogueName the dialogue name, relative to the language directory and without any
	 *                 file extension (e.g. "subdirectory/basic").
	 * @param resourceType the type of the resource as either {@link ResourceType#SCRIPT} or
	 *                 {@link ResourceType#TRANSLATION}.
	 */
	public ResourcePointer(String language, String dialogueName, ResourceType resourceType) {
		this.setLanguage(language);
		this.setDialogueName(dialogueName);
		this.resourceType = resourceType;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the language of this {@link ResourcePointer}, which is the name of the "language
	 * directory", which is the direct subdirectory of the project's root directory (e.g. "en" - for
	 * English).
	 *
	 * @return the language of this {@link ResourcePointer}.
	 */
	public String getLanguage() {
		return this.language;
	}

	/**
	 * Sets the language of this {@link ResourcePointer}, which is the name of the "language
	 * directory", which is the direct subdirectory of the project's root directory (e.g. "en" - for
	 * English).
	 *
	 * @param language the language of this {@link ResourcePointer}.
	 */
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * Returns the dialogue name of this {@link ResourcePointer}, which is the storage-agnostic
	 * identifier for the dialogue, relative to the language folder and without any file extension
	 * (e.g. "subdirectory/basic").
	 *
	 * @return the dialogue name of this {@link ResourcePointer}.
	 * @throws IllegalStateException if {@code dialogueName} has not been set.
	 */
	public String getDialogueName() {
		if (dialogueName == null)
			throw new IllegalStateException("Cannot get dialogue name: dialogueName is not set.");
		return this.dialogueName;
	}

	/**
	 * Sets the dialogue name of this {@link ResourcePointer}, which is the storage-agnostic
	 * identifier for the dialogue, relative to the language folder and without any file extension
	 * (e.g. "subdirectory/basic").
	 *
	 * @param dialogueName the dialogue name of this {@link ResourcePointer}.
	 */
	public void setDialogueName(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	/**
	 * Returns the type of this {@link ResourcePointer} as either {@link ResourceType#SCRIPT} or
	 * {@link ResourceType#TRANSLATION}.
	 *
	 * @return the type of this {@link ResourcePointer}.
	 */
	public ResourceType getResourceType() {
		return resourceType;
	}

	/**
	 * Sets the type of this {@link ResourcePointer} as either {@link ResourceType#SCRIPT} or
	 * {@link ResourceType#TRANSLATION}.
	 *
	 * @param resourceType the type of this {@link ResourcePointer}.
	 */
	public void setResourceType(ResourceType resourceType) {
		this.resourceType = resourceType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(language, dialogueName, resourceType);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj == null || obj.getClass() != getClass())
			return false;
		ResourcePointer other = (ResourcePointer) obj;
		return Objects.equals(language, other.language)
			&& Objects.equals(dialogueName, other.dialogueName)
			&& Objects.equals(resourceType, other.resourceType);
	}

	@Override
	public String toString() {
		return "Dialogue Branch Resource '" + this.getDialogueName() + "' in language '"
			+ this.getLanguage() + "'.";
	}

}
