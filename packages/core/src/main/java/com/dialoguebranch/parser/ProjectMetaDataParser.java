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

package com.dialoguebranch.parser;

import com.dialoguebranch.model.Language;
import com.dialoguebranch.model.LanguageMap;
import com.dialoguebranch.model.LanguageSet;
import com.dialoguebranch.model.ProjectMetaData;
import com.dialoguebranch.script.model.FileStorageSource;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.xml.AbstractSimpleSAXHandler;
import nl.rrd.utils.xml.SimpleSAXHandler;
import nl.rrd.utils.xml.SimpleSAXParser;
import org.xml.sax.Attributes;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ProjectMetaDataParser {

    public static ProjectMetaData parse(File metaDataFile) throws ParseException, IOException {
        SimpleSAXHandler<ProjectMetaData> xmlHandler = new ProjectMetaDataXMLHandler();
        SimpleSAXParser<ProjectMetaData> parser = new SimpleSAXParser<>(xmlHandler);
        ProjectMetaData projectMetaData = parser.parse(metaDataFile);
        projectMetaData.setBasePath(metaDataFile.getParent());
        projectMetaData.setStorageSource(new FileStorageSource(metaDataFile));
        return projectMetaData;
    }

    /**
     * TODO: Test error handling.
     * TODO: Check for duplicate languages.
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
                    throw new ParseException("Expected element 'dlb-project' while parsing DialogueBranch project metadata, found '"+name+"'.");
                } else {
                    result = new ProjectMetaData();
                    if(attributes.getValue("name") == null) {
                        throw new ParseException("Missing attribute 'name' in element 'dlb-project' while parsing DialogueBranch project metadata.");
                    } else {
                        result.setName(attributes.getValue("name"));
                    }
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
                        throw new ParseException("Unexpected element while parsing DialogueBranch project metadata: '"+name+"'");
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
        private SimpleSAXHandler<LanguageSet> languageSetHandler = null;

        @Override
        public void startElement(String name, Attributes attributes, List<String> parents)
                throws ParseException {
            if(name.equals("language-map")) {
                result = new LanguageMap();
            } else if(name.equals("language-set")) {
                languageSetHandler = new LanguageSetXMLHandler();
                languageSetHandler.startElement(name,attributes,parents);
            } else {
                if(languageSetHandler != null)
                    languageSetHandler.startElement(name,attributes,parents);
            }
        }

        @Override
        public void endElement(String name, List<String> parents) throws ParseException {
            if(languageSetHandler != null) languageSetHandler.endElement(name,parents);
            if(name.equals("language-set") && languageSetHandler != null) {
                LanguageSet languageSet = languageSetHandler.getObject();
                result.addLanguageSet(languageSet);
                languageSetHandler = null;
            }
        }

        @Override
        public void characters(String ch, List<String> parents) { }

        @Override
        public LanguageMap getObject() {
            return result;
        }
    }

    private static class LanguageSetXMLHandler extends AbstractSimpleSAXHandler<LanguageSet> {

        private LanguageSet result;
        private SimpleSAXHandler<Language> languageHandler;

        @Override
        public void startElement(String name, Attributes attributes, List<String> parents)
                throws ParseException {
            if(name.equals("language-set")) {
                result = new LanguageSet();
            } else if(name.equals("source-language") || name.equals("translation-language")) {
                languageHandler = new LanguageXMLHandler();
                languageHandler.startElement(name,attributes,parents);
            } else {
                if(languageHandler != null) languageHandler.startElement(name,attributes,parents);
            }
        }

        @Override
        public void endElement(String name, List<String> parents) throws ParseException {
            if(languageHandler != null) languageHandler.endElement(name,parents);
            if(name.equals("source-language") && languageHandler != null) {
                Language sourceLanguage = languageHandler.getObject();
                result.setSourceLanguage(sourceLanguage);
                languageHandler = null;
            } else if(name.equals("translation-language") && languageHandler != null) {
                Language translationLanguage = languageHandler.getObject();
                result.addTranslationLanguage(translationLanguage);
                languageHandler = null;
            }
        }

        @Override
        public void characters(String ch, List<String> parents) { }

        @Override
        public LanguageSet getObject() {
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
