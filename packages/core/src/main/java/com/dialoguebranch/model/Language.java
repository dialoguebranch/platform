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

package com.dialoguebranch.model;

/**
 * A {@link Language} defines a language used in a DialogueBranch project with a given name and
 * language code. The 'code' is preferably a specific ISO3 code, an ISO1 code, or for languages that
 * don't actually exist (e.g. "Klingon", "Orcish") a made up code that is not assigned to any
 * existing language.
 *
 * <p>Note: Language codes *must* be unique within a given Dialogue Branch project.</p>
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class Language {

	/** The name of this language (e.g. "English") */
	private String name;

	/** The language code for this language (e.g. "en") */
	private String code;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an empty instance of a {@link Language}.
	 */
	public Language() { }

	/**
	 * Creates an instance of a {@link Language} with given {@code name} and {@code code}.
	 *
	 * @param name the name of the language
	 * @param code the code (ISO3, ISO1, or made-up) for this {@link Language}.
	 */
	public Language(String name, String code) {
		this.name = name;
		this.code = code;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of this {@link Language}.
	 *
	 * @return the name of this {@link Language}.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this {@link Language}.
	 *
	 * @param name the name of this {@link Language}.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the code of this {@link Language}.
	 *
	 * @return the code of this {@link Language}.
	 */
	public String getCode() {
		return code;
	}

	/**
	 * Sets the code of this {@link Language}.
	 *
	 * @param code the code of this {@link Language}.
	 */
	public void setCode(String code) {
		this.code = code;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	@Override
	public String toString() {
		return name+" ("+code+")";
	}

}
