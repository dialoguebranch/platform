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

import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import com.dialoguebranch.model.common.ProjectMetaData;
import nl.rrd.utils.xml.XMLWriter;

import java.io.IOException;

/**
 * Utility class that serialises a {@link ProjectMetaData} object to XML using an
 * {@link XMLWriter}.
 *
 * @author Harm op den Akker
 */
public class ProjectMetaDataWriter {

    /** Utility class — no instances. */
    private ProjectMetaDataWriter() {}

    /**
     * Writes the given {@link ProjectMetaData} to an XML file using the supplied {@link XMLWriter}.
     * The writer is closed when this method returns.
     *
     * @param writer the XML writer to write to.
     * @param projectMetaData the project metadata to serialise.
     * @throws IOException if a writing error occurs.
     */
    public static void writeToXMLFile(XMLWriter writer, ProjectMetaData projectMetaData) throws IOException {
        writer.writeStartElement("dlb-project");
        writer.writeAttribute("name",projectMetaData.getName());
        if (projectMetaData.getSlug() != null) {
            writer.writeAttribute("slug", projectMetaData.getSlug());
        }
        writer.writeAttribute("version",projectMetaData.getVersion());

        writer.writeStartElement("description");
        writer.writeCharacters(projectMetaData.getDescription());
        writer.writeEndElement(); // description

        writeLanguageMapXML(writer,projectMetaData.getLanguageMap());

        writer.writeEndElement(); // dlb-project
        writer.close();
    }

    /**
     * Writes the given {@link LanguageMap} as a {@code <language-map>} XML element containing a
     * {@code <source-language>} child and one {@code <translation-language>} child per
     * translation language.
     *
     * @param writer the XML writer to write to.
     * @param languageMap the language map to serialise.
     * @throws IOException if a writing error occurs.
     */
    public static void writeLanguageMapXML(XMLWriter writer, LanguageMap languageMap) throws IOException {
        writer.writeStartElement("language-map");

        if(languageMap.getSourceLanguage() != null) {
            writer.writeStartElement("source-language");
            writer.writeAttribute("name",languageMap.getSourceLanguage().getName());
            writer.writeAttribute("code",languageMap.getSourceLanguage().getCode());
            writer.writeEndElement(); // source-language
        }

        for(Language language : languageMap.getTranslationLanguages()) {
            writer.writeStartElement("translation-language");
            writer.writeAttribute("name",language.getName());
            writer.writeAttribute("code",language.getCode());
            writer.writeEndElement();
        }

        writer.writeEndElement(); // language-map
    }

}
