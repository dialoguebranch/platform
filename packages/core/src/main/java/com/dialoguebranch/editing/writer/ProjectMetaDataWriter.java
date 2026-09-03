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

package com.dialoguebranch.editing.writer;

import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Utility class that serialises a {@link ProjectMetaData} object to a {@code dlb-project.xml}
 * document, using the JDK's DOM / {@link Transformer} APIs.
 *
 * @author Harm op den Akker
 */
public class ProjectMetaDataWriter {

	/** Utility class — no instances. */
	private ProjectMetaDataWriter() {}

	/**
	 * Writes the given {@link ProjectMetaData} as an indented, UTF-8 {@code dlb-project.xml}
	 * document to {@code out}. The stream is written to but not closed.
	 *
	 * @param out the stream to write the XML document to.
	 * @param projectMetaData the project metadata to serialise.
	 * @throws IOException if the document cannot be built or written.
	 */
	public static void writeToXMLFile(OutputStream out, ProjectMetaData projectMetaData)
			throws IOException {
		Document document = newDocument();

		Element root = document.createElement("dlb-project");
		document.appendChild(root);
		root.setAttribute("name", projectMetaData.getName());
		if (projectMetaData.getSlug() != null)
			root.setAttribute("slug", projectMetaData.getSlug());
		root.setAttribute("version", projectMetaData.getVersion());

		Element description = document.createElement("description");
		description.setTextContent(projectMetaData.getDescription());
		root.appendChild(description);

		appendLanguageMap(document, root, Objects.requireNonNull(
				projectMetaData.getLanguageMap(),
				"Cannot write project metadata without a language map"));

		writeDocument(document, out);
	}

	/**
	 * Appends a {@code <language-map>} element to {@code parent}, holding a
	 * {@code <source-language>} child (if the map has one) and one
	 * {@code <translation-language>} child per translation language.
	 */
	private static void appendLanguageMap(Document document, Element parent,
			LanguageMap languageMap) {
		Element languageMapElement = document.createElement("language-map");
		parent.appendChild(languageMapElement);

		Language sourceLanguage = languageMap.getSourceLanguage();
		if (sourceLanguage != null) {
			languageMapElement.appendChild(
					languageElement(document, "source-language", sourceLanguage));
		}
		for (Language language : languageMap.getTranslationLanguages()) {
			languageMapElement.appendChild(
					languageElement(document, "translation-language", language));
		}
	}

	private static Element languageElement(Document document, String tagName, Language language) {
		Element element = document.createElement(tagName);
		if (language.getName() != null)
			element.setAttribute("name", language.getName());
		if (language.getCode() != null)
			element.setAttribute("code", language.getCode());
		return element;
	}

	private static Document newDocument() throws IOException {
		try {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException ex) {
			throw new IOException("Cannot create an XML document builder: " + ex.getMessage(), ex);
		}
	}

	private static void writeDocument(Document document, OutputStream out) throws IOException {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.transform(new DOMSource(document), new StreamResult(out));
		} catch (TransformerException ex) {
			throw new IOException("Cannot write the XML document: " + ex.getMessage(), ex);
		}
	}

}
