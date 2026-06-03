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
 * Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
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
package com.dialoguebranch.script.parser;

import com.dialoguebranch.model.*;
import com.dialoguebranch.parser.ProjectMetaDataParser;
import com.dialoguebranch.script.model.EditableProject;
import com.dialoguebranch.script.model.FileStorageSource;
import com.dialoguebranch.script.model.ScriptTreeNode;
import nl.rrd.utils.exception.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * An {@link EditableProjectParser} is a collection of static methods that can be used to read an
 * {@link EditableProject} from a file.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class EditableProjectParser {

    public static EditableProject read(File metaDataFile) throws IOException, ParseException {

        ProjectMetaData projectMetaData = ProjectMetaDataParser.parse(metaDataFile);

        Map<Language, ScriptTreeNode> availableScripts = new HashMap<>();

        // Add file listings...
        for(LanguageSet ls : projectMetaData.getLanguageMap().getLanguageSets()) {
            Language sourceLanguage = ls.getSourceLanguage();
            ScriptTreeNode sourceLanguageTree = processLanguage(
                    projectMetaData.getBasePath(),
                    sourceLanguage,
                    true);
            availableScripts.put(sourceLanguage,sourceLanguageTree);

            for(Language translationLanguage : ls.getTranslationLanguages()) {
                ScriptTreeNode translationLanguageTree = processLanguage(
                        projectMetaData.getBasePath(),
                        translationLanguage,
                        false);
                availableScripts.put(translationLanguage,translationLanguageTree);
            }

        }

        return new EditableProject(projectMetaData,availableScripts);
    }

    private static ScriptTreeNode processLanguage(String basePath, Language language,
                                                boolean isSourceLanguage) {

        String languageFolder = basePath + language.getCode();
        File languageRoot = new File(languageFolder);

        ScriptTreeNode availableScriptsRoot = new ScriptTreeNode(null,
                new FileStorageSource(languageRoot),
                ResourceType.FOLDER,
                language.getCode());

        if (!languageRoot.isDirectory()) {
            if (!languageRoot.mkdir()) {
                // Language folder didn't exist, and wasn't able to create, have to give up
                return null; //TODO: Error handle this case
            }
        }

        return processLanguageFolder(availableScriptsRoot, isSourceLanguage);
    }

    /**
     * Recursively traverse a given folder and extract all the relevant fileNames from it.
     *
     * @param availableScriptsRoot the root of the ScriptNodeTree in which to collect results.
     * @param isSourcePath true if this is a source language directory, false if it is a
     *                     translation language
     * @return the given {@link ScriptTreeNode} with all its found child nodes attached
     */
    private static ScriptTreeNode processLanguageFolder(ScriptTreeNode availableScriptsRoot,
                                                      boolean isSourcePath) {
        File rootFolder =
                ((FileStorageSource)availableScriptsRoot.getStorageSource()).getSourceFile();
        for (File f : Objects.requireNonNull(rootFolder.listFiles())) {

            if(f.isDirectory()) {
                ScriptTreeNode folderNode = new ScriptTreeNode(availableScriptsRoot,
                        new FileStorageSource(f), ResourceType.FOLDER,f.getName());
                availableScriptsRoot.addChild(folderNode);
                processLanguageFolder(folderNode,isSourcePath);
            } else {
                String validExtension;
                ResourceType resourceType;
                if(isSourcePath) {
                    validExtension = Constants.DLB_SCRIPT_FILE_EXTENSION;
                    resourceType = ResourceType.SCRIPT;
                } else {
                    validExtension = Constants.DLB_TRANSLATION_FILE_EXTENSION;
                    resourceType = ResourceType.TRANSLATION;
                }

                if(f.getAbsolutePath().endsWith(validExtension)) {
                    String scriptName = f.getName()
                            .substring(0,f.getName().length()-validExtension.length());
                    ScriptTreeNode fileNode = new ScriptTreeNode(availableScriptsRoot,
                            new FileStorageSource(f), resourceType,scriptName);
                    availableScriptsRoot.addChild(fileNode);
                }
            }

        }
        return availableScriptsRoot;
    }

}
