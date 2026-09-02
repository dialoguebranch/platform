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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.model.common.FileStorageSource;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.xml.AbstractSimpleSAXHandler;
import nl.rrd.utils.xml.SimpleSAXHandler;
import nl.rrd.utils.xml.SimpleSAXParser;
import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class that parses a Dialogue Branch project metadata XML file into a
 * {@link ProjectMetaData} object.
 *
 * @author Harm op den Akker
 */
public class ProjectMetaDataParser {

	/** Utility class — no instances. */
	private ProjectMetaDataParser() {}

	/**
	 * Parses the given project metadata XML file.
	 * @param metaDataFile the project metadata ({@code .xml}) file.
	 * @return the parsed {@link ProjectMetaData}.
	 * @throws ParseException if the XML content is invalid.
	 * @throws IOException if the file cannot be read.
	 */
	public static ProjectMetaData parse(File metaDataFile) throws ParseException, IOException {
		SimpleSAXHandler<ProjectMetaData> xmlHandler = new ProjectMetaDataXMLHandler();
		SimpleSAXParser<ProjectMetaData> parser = new SimpleSAXParser<>(xmlHandler);
		ProjectMetaData projectMetaData = parser.parse(metaDataFile);
		projectMetaData.setBasePath(metaDataFile.getParent());
		projectMetaData.setStorageSource(new FileStorageSource(metaDataFile));
		return projectMetaData;
	}

	/**
	 * Validates the language configuration of a parsed {@link ProjectMetaData}: every source and
	 * translation {@link Language} must have a non-blank {@code code}, and no two of them may
	 * share a code (compared case-insensitively).
	 *
	 * @param languageMap the parsed language map, or {@code null} if the metadata had no
	 *                    {@code language-map} element.
	 * @throws ParseException if a language has no code, or two languages share a code.
	 */
	private static void validateLanguageMap(@Nullable LanguageMap languageMap) throws ParseException {
		if (languageMap == null)
			return;
		List<Language> languages = new ArrayList<>();
		if (languageMap.getSourceLanguage() != null)
			languages.add(languageMap.getSourceLanguage());
		languages.addAll(languageMap.getTranslationLanguages());

		Set<String> seenCodes = new HashSet<>();
		for (Language language : languages) {
			String code = language.getCode();
			if (code == null || code.isBlank()) {
				throw new ParseException("Missing 'code' attribute on a language in the " +
						"Dialogue Branch project metadata's language-map.");
			}
			if (!seenCodes.add(code.toLowerCase())) {
				throw new ParseException("Duplicate language code '" + code + "' in the " +
						"Dialogue Branch project metadata's language-map: each source and " +
						"translation language must have a distinct code.");
			}
		}
	}

	/**
	 * SAX handler for the top-level {@code dlb-project} element and its {@code description} and
	 * {@code language-map} children.
	 */
	private static class ProjectMetaDataXMLHandler
			extends AbstractSimpleSAXHandler<ProjectMetaData> {

		private ProjectMetaData result;
		private int rootLevel = 0;
		private boolean inDescription = false;
		private SimpleSAXHandler<LanguageMap> languageMapHandler = null;

		@Override
		public void startElement(String name, Attributes attributes, List<String> parents) throws ParseException {

			if(rootLevel == 0) {
				if(!name.equals("dlb-project")) {
					throw new ParseException("Expected element 'dlb-project' while parsing Dialogue Branch project metadata, found '"+name+"'.");
				} else {
					result = new ProjectMetaData();
					if(attributes.getValue("name") == null) {
						throw new ParseException("Missing attribute 'name' in element 'dlb-project' while parsing Dialogue Branch project metadata.");
					} else {
						result.setName(attributes.getValue("name"));
					}
					// Optional — only present in metadata produced by the web service's Export
					// Project feature; absent from hand-authored or classpath seed metadata.
					result.setSlug(attributes.getValue("slug"));
					if(attributes.getValue("version") != null) {
						result.setVersion(attributes.getValue("version"));
					} else {
						result.setVersion("");
					}
					rootLevel++;
				}
			} else if(rootLevel == 1) {
				if(name.equals("description")) {
					inDescription = true;
				} else if(name.equals("language-map")) {
					languageMapHandler = new LanguageMapXMLHandler();
					languageMapHandler.startElement(name,attributes,parents);
				} else {
					if(languageMapHandler != null) {
						languageMapHandler.startElement(name,attributes,parents);
					} else {
						throw new ParseException("Unexpected element while parsing Dialogue Branch project metadata: '"+name+"'");
					}
				}
			}
		}

		@Override
		public void endElement(String name, List<String> parents) throws ParseException {
			if(languageMapHandler != null) {
				languageMapHandler.endElement(name,parents);
			} else if (name.equals("description")) inDescription = false;

			if(name.equals("language-map") && languageMapHandler != null) {
				result.setLanguageMap(languageMapHandler.getObject());
				validateLanguageMap(result.getLanguageMap());
			}
		}

		@Override
		public void characters(String ch, List<String> parents) {
			if(inDescription) {
				result.setDescription(ch);
			}
		}

		@Override
		public ProjectMetaData getObject() {
			return result;
		}
	}

	private static class LanguageMapXMLHandler extends AbstractSimpleSAXHandler<LanguageMap> {

		private LanguageMap result = null;
		private SimpleSAXHandler<Language> languageHandler = null;

		@Override
		public void startElement(String name, Attributes attributes, List<String> parents)
				throws ParseException {
			if(name.equals("language-map")) {
				result = new LanguageMap();
			} else if(name.equals("source-language") || name.equals("translation-language")) {
				languageHandler = new LanguageXMLHandler();
				languageHandler.startElement(name,attributes,parents);
			} else {
				if(languageHandler != null)
					languageHandler.startElement(name,attributes,parents);
			}
		}

		@Override
		public void endElement(String name, List<String> parents) throws ParseException {
			if(languageHandler != null) languageHandler.endElement(name,parents);
			if(name.equals("source-language") && languageHandler != null) {
				result.setSourceLanguage(languageHandler.getObject());
				languageHandler = null;
			} else if(name.equals("translation-language") && languageHandler != null) {
				result.addTranslationLanguage(languageHandler.getObject());
				languageHandler = null;
			}
		}

		@Override
		public void characters(String ch, List<String> parents) { }

		@Override
		public LanguageMap getObject() {
			return result;
		}
	}

	private static class LanguageXMLHandler extends AbstractSimpleSAXHandler<Language> {

		private Language result;

		@Override
		public void startElement(String name, Attributes attributes, List<String> parents) {
			if(name.equals("source-language") || name.equals("translation-language")) {
				result = new Language();
				result.setCode(attributes.getValue("code"));
				result.setName(attributes.getValue("name"));
			}
		}

		@Override
		public void endElement(String name, List<String> parents) { }

		@Override
		public void characters(String ch, List<String> parents) { }

		@Override
		public Language getObject() {
			return result;
		}
	}

}
