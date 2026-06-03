/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
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

package com.dialoguebranch.writer;

import com.dialoguebranch.model.Language;
import com.dialoguebranch.model.LanguageMap;
import com.dialoguebranch.model.LanguageSet;
import com.dialoguebranch.model.ProjectMetaData;
import nl.rrd.utils.xml.XMLWriter;

import java.io.IOException;

public class ProjectMetaDataWriter {

    /**
     * Writes this {@link ProjectMetaData} to file using the given {@link XMLWriter}.
     *
     * @param writer the XML writer
     * @throws IOException if a writing error occurs
     */
    public static void writeToXMLFile(XMLWriter writer, ProjectMetaData projectMetaData) throws IOException {
        writer.writeStartElement("dlb-project");
        writer.writeAttribute("name",projectMetaData.getName());
        writer.writeAttribute("version",projectMetaData.getVersion());

        writer.writeStartElement("description");
        writer.writeCharacters(projectMetaData.getDescription());
        writer.writeEndElement(); // description

        writeLanguageMapXML(writer,projectMetaData.getLanguageMap());

        writer.writeEndElement(); // dlb-project
        writer.close();
    }

    public static void writeLanguageMapXML(XMLWriter writer, LanguageMap languageMap) throws IOException {
        writer.writeStartElement("language-map");

        for(LanguageSet languageSet : languageMap.getLanguageSets()) {
            writeLanguageSetXML(writer,languageSet);
        }

        writer.writeEndElement(); // language-map
    }

    public static void writeLanguageSetXML(XMLWriter writer, LanguageSet languageSet) throws IOException {
        writer.writeStartElement("language-set");

        writer.writeStartElement("source-language");
        writer.writeAttribute("name",languageSet.getSourceLanguage().getName());
        writer.writeAttribute("code",languageSet.getSourceLanguage().getCode());
        writer.writeEndElement(); // source-language

        for(Language language : languageSet.getTranslationLanguages()) {
            writer.writeStartElement("translation-language");
            writer.writeAttribute("name",language.getName());
            writer.writeAttribute("code",language.getCode());
            writer.writeEndElement();
        }

        writer.writeEndElement(); // language-set
    }

}
