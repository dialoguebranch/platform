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

import com.dialoguebranch.exception.DuplicateLanguageCodeException;
import com.dialoguebranch.exception.UnknownLanguageCodeException;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link ProjectMetaData} class is the object representation of a Dialogue Branch metadata
 * instance, which may be stored as an .xml file or in some other way. Contains methods for
 * dynamically modifying the contents of a {@link ProjectMetaData} specification while maintaining
 * certain constraints.
 *
 * @author Harm op den Akker
 */
public class ProjectMetaData {

	/** The name of the Dialogue Branch project */
	private String name;

	/** The base path of the Dialogue Branch project */
	private String basePath;

	/** A free-form description of the Dialogue Branch project */
	private String description;

	/** A free-form version indicator of the Dialogue Branch project */
	private String version;

	/** The mapping object of source- and translation languages available in this project */
	private LanguageMap languageMap;

	/** An object that contains information on where/how this ProjectMetaData is stored */
	private StorageSource storageSource;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link ProjectMetaData} object.
	 */
	public ProjectMetaData() { }

	/**
	 * Creates an instance of a {@link ProjectMetaData} object with the given parameters.
	 *
	 * @param name a descriptive name of the Dialogue Branch project.
	 * @param basePath the base path of this Dialogue Branch project, always ending in a file
	 *                 separator character.
	 * @param description a textual description of this Dialogue Branch project.
	 * @param version free-form version information (e.g. v0.1.0).
	 * @param languageMap contains all the languages supported by this Dialogue Branch project.
	 */
	public ProjectMetaData(String name, String basePath, String description, String version,
						   LanguageMap languageMap) {
		this.name = name;
		if(basePath != null && !basePath.endsWith(File.separator)) {
			this.basePath = basePath + File.separator;
		} else {
			this.basePath = basePath;
		}
		this.description = description;
		this.version = version;
		this.languageMap = languageMap;
	}

	/**
	 * Creates an instance of a {@link ProjectMetaData} object with the given parameters.
	 *
	 * @param name a descriptive name of the Dialogue Branch project.
	 * @param basePath the base path of this Dialogue Branch project, always ending in a file
	 *                 separator character.
	 * @param description a textual description of this Dialogue Branch project.
	 * @param version free-form version information (e.g. v0.1.0).
	 * @param languageMap contains all the languages supported by this Dialogue Branch project.
	 * @param storageSource the object that contains information on where/how this ProjectMetaData
	 *                      is stored.
	 */
	public ProjectMetaData(String name, String basePath, String description, String version,
						   LanguageMap languageMap, StorageSource storageSource) {
		this(name,basePath,description,version,languageMap);
		this.storageSource = storageSource;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of the Dialogue Branch project as a String.
	 *
	 * @return the name of the Dialogue Branch project as a String.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this Dialogue Branch project.
	 *
	 * @param name the name of this Dialogue Branch project.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns a String representation of the base path of this Dialogue Branch project, always
	 * ending in the File Separator character.
	 *
	 * @return a String representation of the base path of this Dialogue Branch project.
	 */
	public String getBasePath() {
		return basePath;
	}

	/**
	 * Sets the base path for this Dialogue Branch project as a {@link String}. If the given {@code
	 * basePath} does not end with a File separator character, it will be added.
	 *
	 * @param basePath the base path for this Dialogue Branch project as a {@link String}.
	 */
	public void setBasePath(String basePath) {
		if(basePath != null && !basePath.endsWith(File.separator)) {
			this.basePath = basePath + File.separator;
		} else {
			this.basePath = basePath;
		}
	}

	/**
	 * Returns the free-form description of this Dialogue Branch project.
	 *
	 * @return the free-form description of this Dialogue Branch project.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the free-form description of this Dialogue Branch project.
	 *
	 * @param description the free-form description of this Dialogue Branch project.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the free-form version indicator of this Dialogue Branch project.
	 *
	 * @return the free-form version indicator of this Dialogue Branch project.
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Sets the free-form version indicator of this Dialogue Branch project.
	 *
	 * @param version the free-form version indicator of this Dialogue Branch project.
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * Returns the {@link LanguageMap} that contains a description of all languages supported in
	 * this Dialogue Branch project and their mapping from source- to translation languages.
	 *
	 * @return the {@link LanguageMap} for this {@link ProjectMetaData}.
	 */
	public LanguageMap getLanguageMap() {
		return languageMap;
	}

	/**
	 * Sets the {@link LanguageMap} for this Dialogue Branch project, containing a mapping of all
	 * supported source- and translation languages.
	 *
	 * @param languageMap the {@link LanguageMap} for this Dialogue Branch project.
	 */
	public void setLanguageMap(LanguageMap languageMap) {
		this.languageMap = languageMap;
	}

	/**
	 * Returns the object that contains information on where/how this ProjectMetaData is stored.
	 *
	 * @return the object that contains information on where/how this ProjectMetaData is stored.
	 */
	public StorageSource getStorageSource() {
		return this.storageSource;
	}

	/**
	 * Sets the object that contains information on where/how this ProjectMetaData is stored.
	 *
	 * @param storageSource the object that contains information on where/how this ProjectMetaData
	 *                      is stored.
	 */
	public void setStorageSource(StorageSource storageSource) {
		this.storageSource = storageSource;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Sets the source language of this Dialogue Branch project to a new language with the given
	 * {@code name} and {@code code}. This method will fail with a
	 * {@link DuplicateLanguageCodeException} if a language with the given {@code code} already
	 * exists as a translation language in this Dialogue Branch project.
	 *
	 * @param name the name of the source language.
	 * @param code the code of the source language.
	 * @throws DuplicateLanguageCodeException in case a language with the given {@code code}
	 *                                           already exists in this Dialogue Branch project.
	 */
	public void setSourceLanguage(String name, String code)
			throws DuplicateLanguageCodeException {
		if(languageExists(code))
			throw new DuplicateLanguageCodeException("A language with the given language " +
					"code '"+code+"' is already defined in this DialogueBranch project.",code);

		languageMap.setSourceLanguage(new Language(name,code));
	}

	/**
	 * Attempts to add a new language with the given {@code name} and {@code code} to this Dialogue
	 * Branch project as a translation language. This method will fail with a
	 * {@link DuplicateLanguageCodeException} if a language with the given {@code code} already
	 * exists in this Dialogue Branch project.
	 *
	 * @param name the name of the language to add.
	 * @param code the code of the language to add.
	 * @throws DuplicateLanguageCodeException in case a language with the given {@code code}
	 *                                           already exists in this Dialogue Branch project.
	 */
	public void addTranslationLanguage(String name, String code)
			throws DuplicateLanguageCodeException {
		if(languageExists(code))
			throw new DuplicateLanguageCodeException("A language with the given language " +
					"code '"+code+"' is already defined in this DialogueBranch project.",code);

		languageMap.addTranslationLanguage(new Language(name,code));
	}

	/**
	 * Checks whether a language with the given {@code languageCode} exists in this {@link
	 * LanguageMap}, either as the source language or as a translation language.
	 *
	 * @param languageCode the language code to search for
	 * @return true if the given {@code languageCode} exists, false otherwise
	 */
	public boolean languageExists(String languageCode) {
		if(languageMap.getSourceLanguage() != null
				&& languageMap.getSourceLanguage().getCode().equals(languageCode))
			return true;
		for(Language translationLanguage : languageMap.getTranslationLanguages()) {
			if(translationLanguage.getCode().equals(languageCode)) return true;
		}
		return false;
	}

	/**
	 * Checks whether a language with the given {@code languageCode} is supported by this Dialogue
	 * Branch project (either as its source language or as one of its translation languages).
	 *
	 * @param languageCode the language code to validate.
	 * @throws UnknownLanguageCodeException if no language with the given {@code languageCode} is
	 *                                      supported by this Dialogue Branch project.
	 */
	public void validateLanguageCode(String languageCode) throws UnknownLanguageCodeException {
		if(!languageExists(languageCode)) {
			throw new UnknownLanguageCodeException("Language code '"+languageCode+"' is not " +
					"supported in Dialogue Branch project '"+name+"'.",languageCode);
		}
	}

	/**
	 * Returns a list of {@link Language} objects representing all the different languages supported
	 * in this Dialogue Branch project (the source language, if set, followed by every translation
	 * language).
	 *
	 * @return the list of {@link Language}s, supported in this project.
	 */
	public List<Language> getSupportedLanguages() {
		List<Language> result = new ArrayList<>();

		if(languageMap != null) {
			if(languageMap.getSourceLanguage() != null) {
				result.add(languageMap.getSourceLanguage());
			}
			if(languageMap.getTranslationLanguages() != null) {
				result.addAll(languageMap.getTranslationLanguages());
			}
		}
		return result;
	}

	/**
	 * Returns a list of language codes representing all the supported languages in this Dialogue
	 * Branch project.
	 *
	 * @return the list of all language codes (as Strings) in this project.
	 */
	public List<String> getSupportedLanguageCodes() {
		List<String> result = new ArrayList<>();

		for(Language language : getSupportedLanguages()) {
			result.add(language.getCode());
		}

		return result;
	}

	/**
	 * Returns the source language of this Dialogue Branch project, or {@code null} if none has
	 * been defined.
	 *
	 * @return the source {@link Language} of this Dialogue Branch project, or {@code null}.
	 */
	public Language getSourceLanguage() {
		return languageMap != null ? languageMap.getSourceLanguage() : null;
	}

	/**
	 * Returns the language code of the source language of this Dialogue Branch project, or
	 * {@code null} if none has been defined.
	 *
	 * @return the source language code of this Dialogue Branch project, or {@code null}.
	 */
	public String getSourceLanguageCode() {
		Language source = getSourceLanguage();
		return source != null ? source.getCode() : null;
	}

	/**
	 * Returns a human-readable String representation of this {@link ProjectMetaData}.
	 *
	 * @return a human-readable String representation of this {@link ProjectMetaData}.
	 */
	public String toString() {
		String result = "";
		result += "DialogueBranch Project Metadata:\n";
		result += "[name:"+name+"]\n";
		result += "[basePath:"+basePath+"]\n";
		result += "[description:"+description+"]\n";
		result += "[version:"+version+"]\n";
		if(languageMap != null)
			result += languageMap.toString();
		return result;
	}

}
