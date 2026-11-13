/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.exception.ProjectParseException;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ProjectScriptLoader;
import com.dialoguebranch.i18n.ContextTranslation;
import com.dialoguebranch.i18n.Translatable;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.i18n.Translator;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.DialogueBranchProject;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.common.Speaker;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
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
	private @Nullable ProjectMetaData metaData;
	private List<Speaker> speakers = List.of();
	private final Map<ResourcePointer, Map<TranslationContext.Gender, Dialogue>> userGenderDialogues =
			new HashMap<>();
	private Map<ResourcePointer, Dialogue> dialogues = new LinkedHashMap<>();
	private Map<ResourcePointer, Dialogue> sourceDialogues = new LinkedHashMap<>();
	private Map<ResourcePointer,
			Map<Translatable,List<ContextTranslation>>> translations = new LinkedHashMap<>();
	private Map<String, List<String>> warnings = Map.of();

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

	/**
	 * Loads and parses the project described by the given project metadata file ({@code
	 * dlb-project.xml}), with its scripts and translations in the language folders next to it.
	 *
	 * <p>Parse warnings don't stop the project from loading; they are available from {@link
	 * #getWarnings()}. To get the parse errors one by one instead of as an exception, or to load
	 * a project from somewhere other than a folder on disk, use a {@link ProjectParser}.</p>
	 *
	 * @param projectMetadataFile the project's metadata file.
	 * @return the parsed project.
	 * @throws IOException if a file of the project can't be read.
	 * @throws ProjectParseException if any file of the project has parse errors; it lists them
	 *                               all.
	 * @throws ParseException if the project metadata file itself is invalid.
	 */
	public static ExecutableProject load(Path projectMetadataFile)
			throws IOException, ParseException {
		ProjectScriptLoader loader = new ProjectScriptLoader(projectMetadataFile.toFile());
		ProjectParserResult result = new ProjectParser(loader).parse();
		ExecutableProject project = result.getProject();
		if (!result.getParseErrors().isEmpty() || project == null) {
			throw new ProjectParseException(projectMetadataFile.toString(),
					result.getParseErrors());
		}
		return project;
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
	public @Nullable ProjectMetaData getMetaData() {
		return metaData;
	}

	/**
	 * Sets the {@link ProjectMetaData} associated with this {@link ExecutableProject}.
	 * @param metaData the {@link ProjectMetaData} associated with this {@link ExecutableProject}.
	 */
	public void setMetaData(ProjectMetaData metaData) {
		this.metaData = metaData;
	}

	/**
	 * Returns the warnings found while parsing this project, per file. They don't stop the
	 * project from running, but may point at mistakes, such as a translation that matches no
	 * text in its dialogue.
	 *
	 * @return the parse warnings, per file; empty if there were none.
	 */
	public Map<String, List<String>> getWarnings() {
		return warnings;
	}

	/**
	 * Sets the warnings found while parsing this project, per file.
	 *
	 * @param warnings the parse warnings, per file.
	 */
	public void setWarnings(Map<String, List<String>> warnings) {
		Map<String, List<String>> copy = new LinkedHashMap<>();
		warnings.forEach((file, list) -> copy.put(file, List.copyOf(list)));
		this.warnings = Collections.unmodifiableMap(copy);
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Returns the code of the language the project's dialogues are written in: the source
	 * language from the project's metadata, or, for a project without metadata, the language of
	 * its source dialogues.
	 *
	 * @return the source language code, or {@code null} for a project without metadata and
	 *         without dialogues.
	 */
	public @Nullable String getSourceLanguage() {
		if (metaData != null && metaData.getSourceLanguageCode() != null)
			return metaData.getSourceLanguageCode();
		for (ResourcePointer pointer : sourceDialogues.keySet()) {
			if (pointer.getLanguage() != null)
				return pointer.getLanguage();
		}
		return null;
	}

	/**
	 * Returns the dialogue with the given name, in the project's source language.
	 *
	 * @param dialogueName the dialogue's name, including its folders, such as {@code
	 *                     "intro/welcome"}; a leading {@code "/"} is allowed.
	 * @return the dialogue.
	 * @throws ExecutionException ({@link ExecutionException.Type#DIALOGUE_NOT_FOUND}) if the
	 *                            project has no dialogue with that name.
	 */
	public Dialogue getDialogue(String dialogueName) throws ExecutionException {
		return getDialogue(dialogueName, null);
	}

	/**
	 * Returns the dialogue with the given name, in the given language. If the dialogue isn't
	 * available in that language, or no language is given, it is returned in the project's
	 * source language. The language must match exactly: {@code "nl"} doesn't select {@code
	 * "nl-NL"}.
	 *
	 * @param dialogueName the dialogue's name, including its folders, such as {@code
	 *                     "intro/welcome"}; a leading {@code "/"} is allowed.
	 * @param language the language code, or {@code null} for the source language.
	 * @return the dialogue.
	 * @throws ExecutionException ({@link ExecutionException.Type#DIALOGUE_NOT_FOUND}) if the
	 *                            project has no dialogue with that name.
	 */
	public Dialogue getDialogue(String dialogueName, @Nullable String language)
			throws ExecutionException {
		return Objects.requireNonNull(dialogues.get(getDialoguePointer(dialogueName, language)));
	}

	/**
	 * Returns the {@link ResourcePointer} of the dialogue with the given name, in the given
	 * language, chosen as for {@link #getDialogue(String, String)}. Its language is the one the
	 * dialogue is actually available in.
	 *
	 * @param dialogueName the dialogue's name, including its folders, such as {@code
	 *                     "intro/welcome"}; a leading {@code "/"} is allowed.
	 * @param language the language code, or {@code null} for the source language.
	 * @return the dialogue's resource pointer.
	 * @throws ExecutionException ({@link ExecutionException.Type#DIALOGUE_NOT_FOUND}) if the
	 *                            project has no dialogue with that name.
	 */
	public ResourcePointer getDialoguePointer(String dialogueName, @Nullable String language)
			throws ExecutionException {
		String name = dialogueName.startsWith(DialogueBranchConstants.DLB_PATH_SEPARATOR)
				? dialogueName.substring(DialogueBranchConstants.DLB_PATH_SEPARATOR.length())
				: dialogueName;
		ResourcePointer source = null;
		for (ResourcePointer pointer : dialogues.keySet()) {
			if (!pointer.getDialogueName().equals(name))
				continue;
			if (language != null && language.equals(pointer.getLanguage()))
				return pointer;
			if (sourceDialogues.containsKey(pointer))
				source = pointer;
		}
		if (source == null) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
					"Dialogue not found: " + dialogueName);
		}
		return source;
	}

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
	public @Nullable Dialogue getTranslatedDialogue(ResourcePointer dialogueDescription,
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

	/**
	 * Sets the project's speakers, whose genders the translations use (#399). Set by
	 * {@link com.dialoguebranch.execution.parser.ProjectParser}.
	 *
	 * @param speakers the project's speakers.
	 */
	public void setSpeakers(List<Speaker> speakers) {
		this.speakers = List.copyOf(speakers);
	}

	/**
	 * Returns the dialogue for the given description as translated for an end user of the given
	 * gender (#400). Only a translation that uses the user's gender (a {@code male_addressee},
	 * {@code female_addressee}, {@code male_user} or {@code female_user} context, or a gender
	 * context in the {@code _user} group) differs
	 * per gender; every other dialogue, and an undefined gender, is the one in
	 * {@link #getDialogues()}. A translation for a male or female user is made the first time
	 * it is asked for, and kept.
	 *
	 * @param dialogueDescription the dialogue description (name and language).
	 * @param userGender the end user's gender.
	 * @return the dialogue, or {@code null} if there is none for the description.
	 */
	public synchronized @Nullable Dialogue getDialogueForUserGender(
			ResourcePointer dialogueDescription, TranslationContext.Gender userGender) {
		Dialogue dialogue = dialogues.get(dialogueDescription);
		Map<Translatable, List<ContextTranslation>> dialogueTranslations =
				translations.get(dialogueDescription);
		if (dialogue == null || userGender == TranslationContext.Gender.UNDEFINED
				|| dialogueTranslations == null || !usesUserGender(dialogueTranslations))
			return dialogue;
		Dialogue source = findSourceDialogue(dialogueDescription.getDialogueName());
		if (source == null)
			return dialogue;
		return userGenderDialogues
				.computeIfAbsent(dialogueDescription, (d) -> new EnumMap<>(TranslationContext.Gender.class))
				.computeIfAbsent(userGender, (gender) -> new Translator(new TranslationContext(gender,
						TranslationContext.forSpeakers(speakers).getAgentGenders()),
						dialogueTranslations).translate(source));
	}

	private static boolean usesUserGender(Map<Translatable, List<ContextTranslation>> translations) {
		for (List<ContextTranslation> list : translations.values()) {
			for (ContextTranslation translation : list) {
				Set<String> context = translation.context();
				if (context.contains("male_addressee") || context.contains("female_addressee")
						|| context.contains("male_user") || context.contains("female_user"))
					return true;
				if (DialogueBranchConstants.DLB_USER_SPEAKER.equals(translation.speaker())
						&& (context.contains("male_speaker") || context.contains("female_speaker")))
					return true;
			}
		}
		return false;
	}

	private @Nullable Dialogue findSourceDialogue(String dialogueName) {
		for (ResourcePointer description : sourceDialogues.keySet()) {
			if (description.getDialogueName().equals(dialogueName))
				return dialogues.get(description);
		}
		return null;
	}
}
