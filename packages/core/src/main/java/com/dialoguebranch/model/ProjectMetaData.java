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

import com.dialoguebranch.exception.DuplicateLanguageCodeException;
import com.dialoguebranch.exception.UnknownLanguageCodeException;
import com.dialoguebranch.script.model.StorageSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link ProjectMetaData} class is the object representation of a Dialogue Branch metadata
 * instance, which may be stored as an .xml file or in some other way. Contains methods for
 * dynamically modifying the contents of a {@link ProjectMetaData} specification while maintaining
 * certain constraints.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
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
	 * @param name a descriptive name of the DialogueBranch project.
	 * @param basePath the base path of this DialogueBranch project, always ending in a file
	 *                 separator character.
	 * @param description a textual description of this DialogueBranch project.
	 * @param version free-form version information (e.g. v0.1.0).
	 * @param languageMap contains all the languages supported by this DialogueBranch project.
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
	 * @param name a descriptive name of the DialogueBranch project.
	 * @param basePath the base path of this DialogueBranch project, always ending in a file
	 *                 separator character.
	 * @param description a textual description of this DialogueBranch project.
	 * @param version free-form version information (e.g. v0.1.0).
	 * @param languageMap contains all the languages supported by this DialogueBranch project.
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
	 * Attempts to set a new language with the given {@code name} and {@code code} to the given
	 * {@link LanguageSet} as the source language in this Dialogue Branch project. This method
	 * will succeed and return {@code true} if and only if a language with the given {@code code}
	 * does not exist yet in the {@link LanguageMap} of this Dialogue Branch project.
	 *
	 * @param name the name of the source language to add.
	 * @param code the code of the source language to add.
	 * @param languageSet the language set to which to add the language
	 * @throws DuplicateLanguageCodeException in case a language with the given {@code code}
	 *                                           already exists in this DialogueBranch project.
	 */
	public void setSourceLanguage(String name, String code, LanguageSet languageSet)
			throws DuplicateLanguageCodeException {
		if(languageExists(code))
			throw new DuplicateLanguageCodeException("A language with the given language " +
					"code '"+code+"' is already defined in this DialogueBranch project.",code);

		languageSet.setSourceLanguage(new Language(name,code));
	}

	/**
	 * Attempts to add a new source language to this DialogueBranch project with the given
	 * {@code name} and {@code code} by creating a new {@link LanguageSet} for it. This method
	 * will fail with a {@link DuplicateLanguageCodeException} if a language with the given
	 * {@code code} already exists in this DialogueBranch project. Otherwise, it will return a
	 * pointer to the newly created {@link LanguageSet}.
	 *
	 * @param name the name of the source language to add.
	 * @param code the code of the source language to add.
	 * @throws DuplicateLanguageCodeException in case a language with the given {@code code}
	 *                                           already exists in this DialogueBranch project.
	 * @return the newly created {@link LanguageSet}
	 */
	public LanguageSet addSourceLanguage(String name, String code)
			throws DuplicateLanguageCodeException {
		if(languageExists(code))
			throw new DuplicateLanguageCodeException("A language with the given language " +
					"code '"+code+"' is already defined in this DialogueBranch project.",code);

		LanguageSet languageSet = new LanguageSet(new Language(name, code));
		languageMap.addLanguageSet(languageSet);
		return languageSet;
	}

	/**
	 * Attempts to add a new language with the given {@code name} and {@code code} to the given
	 * {@link LanguageSet} as a translation language. This method will succeed and return
	 * {@code true} if and only if a language with the given {@code code} does not exist yet
	 * in this {@link LanguageMap}.
	 *
	 * @param name the name of the language to add.
	 * @param code the code of the language to add.
	 * @param languageSet the language set to which to add the language
	 * @throws DuplicateLanguageCodeException in case a language with the given {@code code}
	 *                                           already exists in this DialogueBranch project.
	 */
	public void addTranslationLanguage(String name, String code, LanguageSet languageSet)
			throws DuplicateLanguageCodeException {
		if(languageExists(code))
			throw new DuplicateLanguageCodeException("A language with the given language " +
					"code '"+code+"' is already defined in this DialogueBranch project.",code);

		languageSet.addTranslationLanguage(new Language(name,code));
	}

	/**
	 * Checks whether a language with the given {@code languageCode} exists in this {@link
	 * LanguageMap}.
	 *
	 * @param languageCode the language code to search for
	 * @return true if the given {@code languageCode} exists, false otherwise
	 */
	public boolean languageExists(String languageCode) {
		for(LanguageSet languageSet : languageMap.getLanguageSets()) {
			if(languageSet.getSourceLanguage().getCode().equals(languageCode)) return true;
			for(Language translationLanguage : languageSet.getTranslationLanguages()) {
				if(translationLanguage.getCode().equals(languageCode)) return true;
			}
		}
		return false;
	}

	/**
	 * Returns the {@link LanguageSet} in this DialogueBranch project for which the source
	 * language code matches the given {@code code}.
	 *
	 * @param sourceLanguageCode the language code of the source language for which to look up its
	 *                           {@link LanguageSet}.
	 * @return the {@link LanguageSet} with a source language with the given {@code code}.
	 * @throws UnknownLanguageCodeException if no language set exists with the given source
	 *                                         language code.
	 */
	public LanguageSet getLanguageSetForSourceLanguage(String sourceLanguageCode)
			throws UnknownLanguageCodeException {
		for(LanguageSet languageSet : languageMap.getLanguageSets()) {
			if(languageSet.getSourceLanguage().getCode().equals(sourceLanguageCode))
				return languageSet;
		}
		throw new UnknownLanguageCodeException("No language set found with source language '"
				+sourceLanguageCode+"'.",sourceLanguageCode);
	}

	/**
	 * Returns a list of {@link Language} objects representing all the different languages supported
	 * in this Dialogue Branch project.
	 *
	 * @return the list of {@link Language}s, supported in this project.
	 */
	public List<Language> getSupportedLanguages() {
		List<Language> result = new ArrayList<>();

		if(languageMap != null) {
			List<LanguageSet> languageSets = languageMap.getLanguageSets();
			if(languageSets != null) {
				for(LanguageSet languageSet : languageSets) {
					Language sourceLanguage = languageSet.getSourceLanguage();
					if(sourceLanguage != null) {
						result.add(sourceLanguage);
					}
					List<Language> translationLanguages = languageSet.getTranslationLanguages();
					if(translationLanguages != null) {
                        result.addAll(translationLanguages);
					}
				}
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
	 * Returns a list of all source languages defined in this {@link ProjectMetaData} as {@link
	 * Language} objects.
	 *
	 * @return a list of all source languages defined in this {@link ProjectMetaData} as {@link
	 *         Language} objects.
	 */
	public List<Language> getSourceLanguages() {
		List<Language> result = new ArrayList<>();

		if(languageMap != null) {
			for(LanguageSet languageSet : languageMap.getLanguageSets()) {
				if(languageSet.getSourceLanguage() != null) {
					result.add(languageSet.getSourceLanguage());
				}
			}
		}

		return result;
	}

	/**
	 * Returns a list of language code, representing all available "source" languages as defined in
	 * this {@link ProjectMetaData}.
	 *
	 * @return a list of all source languages as language code strings.
	 */
	public List<String> getSourceLanguageCodes() {
		List<String> result = new ArrayList<>();
		for(Language language : getSourceLanguages()) {
			result.add(language.getCode());
		}
		return result;
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
