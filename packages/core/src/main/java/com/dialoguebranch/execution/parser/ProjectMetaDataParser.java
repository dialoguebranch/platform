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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.model.common.FileStorageSource;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
		ProjectMetaData projectMetaData;
		try (InputStream input = new BufferedInputStream(new FileInputStream(metaDataFile))) {
			XMLStreamReader reader = newInputFactory().createXMLStreamReader(input);
			try {
				projectMetaData = readProject(reader);
			} finally {
				reader.close();
			}
		} catch (XMLStreamException ex) {
			throw new ParseException("Invalid XML while parsing Dialogue Branch project " +
					"metadata: " + ex.getMessage(), ex);
		}
		projectMetaData.setBasePath(metaDataFile.getParent());
		projectMetaData.setStorageSource(new FileStorageSource(metaDataFile));
		return projectMetaData;
	}

	/**
	 * Returns an {@link XMLInputFactory} with DTD processing and external entity resolution
	 * disabled (there is no legitimate DTD in project metadata, and disabling them closes off
	 * XXE / entity-expansion attacks).
	 */
	private static XMLInputFactory newInputFactory() {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		return factory;
	}

	/**
	 * Reads the {@code dlb-project} root element and its {@code description} / {@code language-map}
	 * children.
	 */
	private static ProjectMetaData readProject(XMLStreamReader reader)
			throws XMLStreamException, ParseException {
		if (!nextStartElement(reader)) {
			throw new ParseException("Expected element 'dlb-project' while parsing Dialogue " +
					"Branch project metadata, found no element.");
		}
		if (!reader.getLocalName().equals("dlb-project")) {
			throw new ParseException("Expected element 'dlb-project' while parsing Dialogue " +
					"Branch project metadata, found '" + reader.getLocalName() + "'.");
		}

		ProjectMetaData result = new ProjectMetaData();

		String name = reader.getAttributeValue(null, "name");
		if (name == null) {
			throw new ParseException("Missing attribute 'name' in element 'dlb-project' while " +
					"parsing Dialogue Branch project metadata.");
		}
		result.setName(name);

		// Optional — only present in metadata produced by the web service's Export Project
		// feature; absent from hand-authored or classpath seed metadata.
		String slug = reader.getAttributeValue(null, "slug");
		if (slug != null)
			result.setSlug(slug);

		String version = reader.getAttributeValue(null, "version");
		result.setVersion(version != null ? version : "");

		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT)
				break;
			if (event != XMLStreamConstants.START_ELEMENT)
				continue;
			switch (reader.getLocalName()) {
				case "description" -> result.setDescription(reader.getElementText());
				case "language-map" -> {
					LanguageMap languageMap = readLanguageMap(reader);
					result.setLanguageMap(languageMap);
					validateLanguageMap(languageMap);
				}
				default -> throw new ParseException("Unexpected element while parsing Dialogue " +
						"Branch project metadata: '" + reader.getLocalName() + "'");
			}
		}
		return result;
	}

	/**
	 * Reads a {@code language-map} element (the reader is positioned on its start tag) and its
	 * {@code source-language} / {@code translation-language} children.
	 */
	private static LanguageMap readLanguageMap(XMLStreamReader reader) throws XMLStreamException {
		LanguageMap result = new LanguageMap();
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.END_ELEMENT)
				break;
			if (event != XMLStreamConstants.START_ELEMENT)
				continue;
			switch (reader.getLocalName()) {
				case "source-language" -> result.setSourceLanguage(readLanguage(reader));
				case "translation-language" ->
						result.addTranslationLanguage(readLanguage(reader));
				default -> skipElement(reader);
			}
		}
		return result;
	}

	/**
	 * Reads a {@code source-language} / {@code translation-language} element (the reader is
	 * positioned on its start tag) into a {@link Language}, then advances past its end tag.
	 */
	private static Language readLanguage(XMLStreamReader reader) throws XMLStreamException {
		Language result = new Language();
		result.setCode(reader.getAttributeValue(null, "code"));
		result.setName(reader.getAttributeValue(null, "name"));
		skipElement(reader);
		return result;
	}

	/** Consumes the current element (positioned on its start tag) up to and including its end tag. */
	private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
		int depth = 1;
		while (depth > 0 && reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.START_ELEMENT)
				depth++;
			else if (event == XMLStreamConstants.END_ELEMENT)
				depth--;
		}
	}

	/** Advances to the next {@code START_ELEMENT}, returning {@code false} at end of document. */
	private static boolean nextStartElement(XMLStreamReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			if (reader.next() == XMLStreamConstants.START_ELEMENT)
				return true;
		}
		return false;
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

}
