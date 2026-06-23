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

package com.dialoguebranch.model.common;

import com.dialoguebranch.model.execute.ResourcePointer;

import java.util.List;

/**
 * Defines the common contract for all representations of a Dialogue Branch project, regardless of
 * the underlying storage system or lifecycle stage. Every project has:
 * <ul>
 *   <li>{@link ProjectMetaData} describing the project name, version, supported languages,
 *   and other configuration.</li>
 *   <li>A flat collection of {@link ResourcePointer}s identifying every script and translation
 *   resource in the project, using storage-agnostic dialogue names.</li>
 * </ul>
 *
 * <p>Known implementations:</p>
 * <ul>
 *   <li>{@code ExecutableProject} — the fully parsed, runtime representation used for dialogue
 *   execution.</li>
 *   <li>{@code EditableProject} — the in-editor representation used for authoring and
 *   modification.</li>
 * </ul>
 *
 * @author Harm op den Akker
 */
public interface DialogueBranchProject {

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the {@link ProjectMetaData} associated with this project, or {@code null} if no
	 * metadata has been set.
	 *
	 * @return the {@link ProjectMetaData} for this project.
	 */
	ProjectMetaData getMetaData();

	/**
	 * Sets the {@link ProjectMetaData} associated with this project.
	 *
	 * @param metaData the {@link ProjectMetaData} for this project.
	 */
	void setMetaData(ProjectMetaData metaData);

	/**
	 * Returns a flat list of {@link ResourcePointer}s representing all resources (scripts and
	 * translations) in this project. Each pointer identifies a resource by language and
	 * storage-agnostic dialogue name, without any file extension.
	 *
	 * @return all resource pointers in this project.
	 */
	List<ResourcePointer> getResourcePointers();

}
