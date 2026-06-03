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
package com.dialoguebranch.script.model;

import com.dialoguebranch.exception.DialogueBranchException;
import com.dialoguebranch.exception.FileSystemException;
import com.dialoguebranch.exception.ScriptParseException;
import com.dialoguebranch.i18n.*;
import com.dialoguebranch.model.*;
import com.dialoguebranch.parser.DialogueBranchParser;
import com.dialoguebranch.parser.ParserResult;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link EditableProject} represents a Dialogue Branch project at the state of being edited.
 * It consists of a {@link ProjectMetaData} object representing the metadata information on the
 * project, and a set of {@link EditableScript}s, representing the dialogue branch scripts that
 * may yet still be edited.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class EditableProject extends Editable implements PropertyChangeListener {

    /** The project metadata information */
    private ProjectMetaData projectMetaData;

    /** A mapping of language to roots of trees of script contents */
    private Map<Language,ScriptTreeNode> availableScripts;

    /** The set of "active" EditableScripts representing the scripts in this project */
    private final Map<String,EditableScript> activeScripts;

    /** Stores whether any changes have been made to this project */
    private boolean isModified;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates an instance of a new empty {@link EditableProject} from the given {@link
     * ProjectMetaData}.
     *
     * @param projectMetaData the project meta data object.
     */
    public EditableProject(ProjectMetaData projectMetaData) {
        this.projectMetaData = projectMetaData;
        this.availableScripts = new HashMap<>();
        this.activeScripts = new HashMap<>();
        this.isModified = false;
    }

    /**
     * Creates an instance of a new empty {@link EditableProject} from the given {@link
     * ProjectMetaData} and the map of {@code availableScripts}.
     *
     * @param projectMetaData the project meta data object.
     * @param availableScripts a mapping from {@link Language} to {@link ScriptTreeNode},
     *                         representing the root of the tree of script contents.
     */
    public EditableProject(ProjectMetaData projectMetaData,
                           Map<Language,ScriptTreeNode> availableScripts) {
        this.projectMetaData = projectMetaData;
        this.availableScripts = availableScripts;
        this.activeScripts = new HashMap<>();
        this.isModified = false;
    }

    // ------------------------------------------------------------ //
    // -------------------- Getters & Setters --------------------- //
    // ------------------------------------------------------------ //

    /**
     * Returns the project metadata object for this {@link EditableProject}.
     *
     * @return the project metadata object for this {@link EditableProject}.
     */
    public ProjectMetaData getProjectMetaData() {
        return this.projectMetaData;
    }

    /**
     * Sets the project metadata object for this {@link EditableProject}.
     *
     * @param projectMetaData the project metadata object for this {@link EditableProject}.
     */
    public void setProjectMetaData(ProjectMetaData projectMetaData) {
        ProjectMetaData oldValue = this.projectMetaData;
        this.projectMetaData = projectMetaData;
        this.getPropertyChangeSupport().firePropertyChange(
                PROPERTY_PROJECT_METADATA,oldValue,projectMetaData);
    }

    /**
     * Returns a mapping from {@link Language}s to {@link ScriptTreeNode}s, representing the roots
     * of the trees of available scripts in this project.
     *
     * @return the map of available scripts in this project.
     */
    public Map<Language,ScriptTreeNode> getAvailableScripts() {
        return this.availableScripts;
    }

    /**
     * Returns a {@link ScriptTreeNode} that is the root of a tree of {@link ScriptTreeNode}s,
     * representing the hierarchy of script files available for the given {@code language}, or
     * {@code null} if there are no known script files for the given language.
     *
     * @param language the language for which to retrieve all scripts
     * @return all scripts for the given language, as a pointer to the node of a tree of scripts
     */
    public ScriptTreeNode getAvailableScriptsForLanguage(Language language) {
        return this.availableScripts.get(language);
    }

    /**
     * Returns a {@link ScriptTreeNode} that is the root of a tree of {@link ScriptTreeNode}s,
     * representing the hierarchy of script files available for the language, represented by the
     * given {@code languageCode}, or {@code null} if there are no known script files for a language
     * defined by that given code.
     *
     * @param languageCode the language code for which to retrieve all scripts
     * @return all scripts for the given language, as a pointer to the node of a tree of scripts
     */
    public ScriptTreeNode getAvailableScriptsForLanguage(String languageCode) {
        for(Language language : availableScripts.keySet()) {
            if(language.getCode().equals(languageCode)) return availableScripts.get(language);
        }
        return null;
    }

    /**
     * Sets a mapping from {@link Language} to a list of script names, indicating the full set of
     * available scripts in this project, and informing any {@link PropertyChangeListener}s of the
     * change
     *
     * @param availableScripts the map of available scripts in this project.
     */
    public void setAvailableScripts(Map<Language,ScriptTreeNode> availableScripts) {
        Map<Language,ScriptTreeNode> oldValue = new HashMap<>(this.availableScripts);
        this.availableScripts = availableScripts;
        this.getPropertyChangeSupport().firePropertyChange(
                PROPERTY_PROJECT_AVAILABLE_SCRIPTS,oldValue,this.availableScripts);
    }

    // -------------------------------------------------------- //
    // -------------------- Other Methods --------------------- //
    // -------------------------------------------------------- //

    public void addActiveScript(EditableScript editableScript) {
        activeScripts.put(editableScript.getDialogueName(),editableScript);
        editableScript.addPropertyChangeListener(this);
    }

    public void removeActiveScript(EditableScript editableScript) {
        editableScript.removePropertyChangeListener(this);
        activeScripts.remove(editableScript.getDialogueName());
    }

    public boolean isModified() {
        return this.isModified;
    }

    public void setModified(boolean isModified) {
        boolean oldValue = this.isModified;
        this.isModified = isModified;

        // If this project is set to be _not_ modified, all its active script are also not modified
        if(!isModified) {
            for(EditableScript editableScript : activeScripts.values()) {
                editableScript.setModified(false);
            }
        }
        this.getPropertyChangeSupport()
                .firePropertyChange(PROPERTY_IS_MODIFIED,oldValue,isModified);
    }

    public List<EditableScript> getModifiedScripts() {
        List<EditableScript> result = new ArrayList<>();
        for(EditableScript script : activeScripts.values()) {
            if(script.isModified()) {
                result.add(script);
            }
        }
        return result;
    }

    /**
     * Generates the translation scripts in the given {@code translationLanguageTree} so that there
     * exist a script for every script available in the given {@code sourceLanguageTree}. Returns
     * the number of actually generated scripts (which may be 0 if all of them already existed).
     * This method will recursively traverse the sourceLanguageTree.
     *
     * @param sourceLanguageTree the source language tree
     * @param translationLanguageTree the translation language tree in which to generate translation
     *                                scripts
     * @return the number of actually generated scripts
     * @throws ScriptParseException
     * @throws IOException
     */
    public int generateTranslationFiles(ScriptTreeNode sourceLanguageTree,
                                        ScriptTreeNode translationLanguageTree)
            throws DialogueBranchException, IOException {

        int generatedScriptCount = 0;

        // Loop through all this node's children...
        for(ScriptTreeNode sourceChild : sourceLanguageTree.getChildren()) {

            // Get a reference to the matching node in the translation language tree
            // (Where the 'ResourceType' should match on the folder/non-folder level)
            ScriptTreeNode matchingNode = translationLanguageTree.getMatchingChild(
                    sourceChild.getName(),
                    sourceChild.getResourceType().equals(ResourceType.FOLDER));

            // If the translation tree doesn't have a matching child...
            if(matchingNode == null) {

                // We must create a new node in the translation tree
                StorageSource newStorageSource = null;

                // If the source node is a File, the translation node must also be a File
                if(sourceChild.getStorageSource() instanceof FileStorageSource) {

                    // If the source child is a folder, the translation child must also be a folder
                    if(sourceChild.getResourceType().equals(ResourceType.FOLDER)) {

                        String newFolderName = translationLanguageTree.getStorageSource()
                                .getDescriptor() + File.separator + sourceChild.getName();
                        File newFolder = new File(newFolderName);

                        if (!newFolder.mkdir()) {
                            // The new folder couldn't be created
                            throw new FileSystemException(
                                    "Unable to create directory at " + newFolderName);
                        }

                        newStorageSource = new FileStorageSource(newFolder);

                    } else {
                        String newFileName = translationLanguageTree.getStorageSource()
                                .getDescriptor() + File.separator + sourceChild.getName()
                                + Constants.DLB_TRANSLATION_FILE_EXTENSION;
                        File newScriptFile = new File(newFileName);

                        // Generate an actual translation .json file from the source .dlb file
                        generateTranslationFile(
                               ((FileStorageSource)sourceChild.getStorageSource()).getSourceFile(),
                               newScriptFile);
                        generatedScriptCount++;
                        newStorageSource = new FileStorageSource(newScriptFile);
                    }

                } else {
                    // TODO: In case other storage sources are supported
                }

                // Create the new ScriptTreeNode into the translation Tree, with the newly generated
                // storage source object, the current parent translationRoot, and the same name and
                // type as the corresponding source node.
                matchingNode = new ScriptTreeNode(
                        translationLanguageTree,
                        newStorageSource,
                        sourceChild.getResourceType(),
                        sourceChild.getName());

                translationLanguageTree.addChild(matchingNode);
            }

            // If this child node is a leaf, we are done, otherwise we must recursively
            // Process the rest of the tree
            if(!sourceChild.isLeaf()) {
                generatedScriptCount += generateTranslationFiles(sourceChild,matchingNode);
            }

        }

        return generatedScriptCount;
    }

    public void generateTranslationFile(File dialogueBranchScript, File translationFile) throws IOException {
        DialogueBranchParser dialogueBranchParser = new DialogueBranchParser(dialogueBranchScript);
        ParserResult parserResult = dialogueBranchParser.readDialogue();
        Dialogue sourceDialogue = parserResult.getDialogue();

        String completeFileName = translationFile.getName();
        String fileName = "";
        int pos = completeFileName.lastIndexOf(".");
        if (pos > 0 && pos < (completeFileName.length() - 1)) { // If '.' is not the first or last character.
            fileName = completeFileName.substring(0, pos);
        }

        TranslationFile translationFileObject = new TranslationFile(fileName);

        for (Node node : sourceDialogue.getNodes()) {
            TranslatableExtractor extractor = new TranslatableExtractor();
            List<SourceTranslatable> translatables = extractor.extractFromNode(node);

            for(SourceTranslatable translatable : translatables) {
                String speakerName = translatable.speaker();
                String term = translatable.translatable().toExportFriendlyString();
                String translation = "";
                translationFileObject.addTerm(speakerName,term,translation);
            }

        }

        translationFileObject.writeToFile(translationFile);
    }

    /**
     * Create an EditableTranslationSet from all EditableTranslation scripts referenced in the given
     * {@code translationTree}.
     *
     * @param translationTree
     * @return
     */
    public EditableTranslationSet getEditableTranslationSet(ScriptTreeNode translationTree) {
        if(translationTree.isLeaf()) {
            StorageSource storageSource = translationTree.getStorageSource();
            EditableTranslation editableTranslation = new EditableTranslation(
                    translationTree.getName(),storageSource);
        }

        // TODO: Finish this.
        return null;
    }

    public void generateTranslationTSVs(ScriptTreeNode translationTree) throws IOException {
        if(translationTree.isLeaf()) {
            StorageSource storageSource = translationTree.getStorageSource();
            if(storageSource instanceof FileStorageSource fileStorageSource) {
                File translationFile = fileStorageSource.getSourceFile();

                String completeFileName = translationFile.getName();
                String fileName = "";
                int pos = completeFileName.lastIndexOf(".");
                if (pos > 0 && pos < (completeFileName.length() - 1)) { // If '.' is not the first or last character.
                    fileName = completeFileName.substring(0, pos);
                }

                TranslationFile tf = new TranslationFile(fileName);
                tf.readFromFile(translationFile);

                File csvFile = new File(translationFile.getParent() + File.separator + fileName + ".tsv");

                tf.writeToTSVFile(csvFile);
            }
        } else {
            for(ScriptTreeNode child : translationTree.getChildren()) {
                generateTranslationTSVs(child);
            }
        }
    }


    // ------------------------------------------------------------------- //
    // -------------------- Property Change Listeners -------------------- //
    // ------------------------------------------------------------------- //

    /**
     * This method gets called when a bound property is changed.
     *
     * @param event A PropertyChangeEvent object describing the event source and the property that
     *              has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(event.getPropertyName().equals(PROPERTY_IS_MODIFIED)) {
            boolean modified = (Boolean) event.getNewValue();
            // If any of its active scripts are modified, this project is modified
            if(modified) {
                this.setModified(true);
            }
        }
    }

}
