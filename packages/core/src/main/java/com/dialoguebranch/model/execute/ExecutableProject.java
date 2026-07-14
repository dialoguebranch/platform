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

import com.dialoguebranch.i18n.ContextTranslation;
import com.dialoguebranch.i18n.Translatable;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.i18n.Translator;
import com.dialoguebranch.model.common.DialogueBranchProject;
import com.dialoguebranch.model.common.ProjectMetaData;
import nl.rrd.utils.i18n.I18nLanguageFinder;

import java.util.*;

/**
 * An {@link ExecutableProject} is the fully parsed, runtime representation of a Dialogue Branch
 * project. It is the top-level element of the execution model, holding source dialogues,
 * translated dialogues, and the raw translation maps used to produce them.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class ExecutableProject implements DialogueBranchProject {
	private ProjectMetaData metaData;
	private Map<ResourcePointer, Dialogue> dialogues = new LinkedHashMap<>();
	private Map<ResourcePointer, Dialogue> sourceDialogues = new LinkedHashMap<>();
	private Map<ResourcePointer,
			Map<Translatable,List<ContextTranslation>>> translations = new LinkedHashMap<>();

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an empty {@link ExecutableProject} with no metadata, dialogues, or translations.
	 */
	public ExecutableProject() { }

	/**
	 * Creates an {@link ExecutableProject} with the given {@link ProjectMetaData}.
	 *
	 * @param metaData the metadata describing this project.
	 */
	public ExecutableProject(ProjectMetaData metaData) {
		this.metaData = metaData;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns all available dialogues in this project. This includes source dialogues as well as
	 * translated dialogues with the default {@link TranslationContext}.
	 *
	 * @return the available dialogues (source and translations with default context)
	 */
	public Map<ResourcePointer, Dialogue> getDialogues() {
		return dialogues;
	}

	/**
	 * Sets all available dialogues in this project. This includes source dialogues as well as
	 * translated dialogues with the default {@link TranslationContext}.
	 *
	 * @param dialogues the available dialogues (source and translations with default context)
	 */
	public void setDialogues(Map<ResourcePointer, Dialogue> dialogues) {
		this.dialogues = dialogues;
	}

	/**
	 * Returns the source dialogues. This excludes any translations.
	 *
	 * @return the source dialogues (no translations)
	 */
	public Map<ResourcePointer, Dialogue> getSourceDialogues() {
		return sourceDialogues;
	}

	/**
	 * Sets the source dialogues. This excludes any translations
	 *
	 * @param sourceDialogues the source dialogues (no translations)
	 */
	public void setSourceDialogues(Map<ResourcePointer, Dialogue> sourceDialogues) {
		this.sourceDialogues = sourceDialogues;
	}

	/**
	 * Returns the translations of all phrases per dialogue. This method returns a map from a
	 * dialogue key to a translation map.
	 *
	 * <p>A translation map is a map from a source phrase to a list of translated phrases, with
	 * different contexts.</p>
	 *
	 * @return the translations
	 */
	public Map<ResourcePointer,Map<Translatable,List<ContextTranslation>>>
	getTranslations() {
		return translations;
	}

	/**
	 * Sets the translations of all phrases per dialogue. This method returns a map from a dialogue
	 * key to a translation map.
	 *
	 * <p>A translation map is a map from a source phrase to a list of translated phrases,
	 * with different contexts.</p>
	 *
	 * @param translations the translations
	 */
	public void setTranslations(
			Map<ResourcePointer,Map<Translatable,List<ContextTranslation>>>
					translations) {
		this.translations = translations;
	}

	/**
	 * Returns the {@link ProjectMetaData} associated with this {@link ExecutableProject}, or
	 * {@code null} if no metadata is associated with this project.
	 * @return the {@link ProjectMetaData} associated with this {@link ExecutableProject}.
	 */
	public ProjectMetaData getMetaData() {
		return metaData;
	}

	/**
	 * Sets the {@link ProjectMetaData} associated with this {@link ExecutableProject}.
	 * @param metaData the {@link ProjectMetaData} associated with this {@link ExecutableProject}.
	 */
	public void setMetaData(ProjectMetaData metaData) {
		this.metaData = metaData;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Returns a list of all supported languages in this {@link ExecutableProject}. In the case of a
	 * "simple" Dialogue Branch project (i.e. a folder with .dlb and possibly .json files without a
	 * specific metadata file), this list is derived from the list of {@link ResourcePointer}s in
	 * this {@link ExecutableProject}. If a {@link ProjectMetaData} has been set (and a language map has
	 * been defined therein), this information will be used instead.
	 * @return a list of all supported languages in this {@link ExecutableProject}.
	 */
	public List<String> getLanguages() {
		List<String> result = new ArrayList<>();

		// If no metaData has been defined, scrape languages from the set of available dialogues
		if(metaData == null || metaData.getLanguageMap() == null) {
			for(ResourcePointer fileDescription : dialogues.keySet()) {
				if(!result.contains(fileDescription.getLanguage()))
					result.add(fileDescription.getLanguage());
			}

		// If there is metadata, obtain the list of languages from there
		} else {
			for(Language language : metaData.getSupportedLanguages()) {
				result.add(language.getCode());
			}
		}

		return result;
	}

	/**
	 * Returns a translated dialogue for the specified translation context. This method first
	 * searches a source dialogue for the specified description (name and language). If found, no
	 * translation is needed and the source dialogue is returned. Otherwise, it searches a source
	 * dialogue with the specified dialogue name and a translation set for the specified language.
	 * If found, it translates the dialogue with the translation context, and then returns the
	 * translated dialogue.
	 *
	 * <p>If no source dialogue or translation is found, this method returns null.</p>
	 *
	 * @param dialogueDescription the dialogue description (name and language)
	 * @param context the translation context
	 * @return the translated dialogue or null
	 */
	public Dialogue getTranslatedDialogue(ResourcePointer dialogueDescription,
	                                      TranslationContext context) {
		Dialogue dialogue = sourceDialogues.get(dialogueDescription);
		if (dialogue != null)
			return dialogue;
		Map<Translatable,List<ContextTranslation>> translations =
				this.translations.get(dialogueDescription);
		if (translations == null)
			return null;
		dialogue = findSourceDialogue(dialogueDescription.getDialogueName());
		if (dialogue == null)
			return null;
		Translator translator = new Translator(context, translations);
		return translator.translate(dialogue);
	}

	@Override
	public List<ResourcePointer> getResourcePointers() {
		return new ArrayList<>(dialogues.keySet());
	}

	private Dialogue findSourceDialogue(String dialogueName) {
		List<ResourcePointer> matches = new ArrayList<>();
		for (ResourcePointer description : sourceDialogues.keySet()) {
			if (description.getDialogueName().equals(dialogueName))
				matches.add(description);
		}
		if (matches.isEmpty())
			return null;
		if (matches.size() == 1)
			return dialogues.get(matches.get(0));
		Map<String, ResourcePointer> lngMap = new HashMap<>();
		for (ResourcePointer match : matches) {
			lngMap.put(match.getLanguage(), match);
		}

		// Prefer the source language defined in the project metadata, if any
		if (metaData != null && metaData.getLanguageMap() != null
				&& metaData.getLanguageMap().getSourceLanguage() != null) {
			ResourcePointer sourceMatch = lngMap.get(
					metaData.getLanguageMap().getSourceLanguage().getCode());
			if (sourceMatch != null)
				return dialogues.get(sourceMatch);
		}

		I18nLanguageFinder finder = new I18nLanguageFinder(new ArrayList<>(lngMap.keySet()));
		finder.setUserLocale(Locale.ENGLISH);
		String language = finder.find();
		if (language == null)
			return dialogues.get(matches.get(0));
		else
			return dialogues.get(lngMap.get(language));
	}
}
