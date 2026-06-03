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

import com.dialoguebranch.model.Constants;
import com.dialoguebranch.script.parser.EditableScriptParser;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link EditableScript} models the contents of a Dialogue Branch script that may contain
 * errors, because it is still being written. This class may be used by tools that provide editing
 * functionality.
 *
 * <p>You may ask "What makes a Dialogue Branch Script a Dialogue Branch Script?" When talking about
 * <em>functioning</em> scripts, this answer is strictly defined through the language definition,
 * but when it comes to scripts that are being written, the answer is not so clear.</p>
 *
 * <p>The definition that we use to model this class is designed to be as fault-tolerant as possible
 * while covering the very basics of what entails a Dialogue Branch script. This definition is that
 * a Dialogue Branch Script has a name, a language code and a list of {@link EditableNode}s.</p>
 *
 * <p>How the separation of this script into nodes is handled is irrelevant and depends on the
 * underlying storage mechanism (e.g. files, or a database).</p>
 *
 * <p>This class enforces that a name and language code are provided and will assume default values
 * if {@code null} or empty String values are provided.</p>
 *
 * <p>This class supports the {@link PropertyChangeSupport} pattern and will report all changes that
 * are made to its content after creation.</p>
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class EditableScript extends Editable implements PropertyChangeListener  {

    /** The name of this dialogue (that should be unique within a project) */
    private String dialogueName;

    /** The language code for this dialogue */
    private String languageCode;

    /** An object that contains information on where/how this EditableScript is stored */
    private final StorageSource storageSource;

    /** The List of ScriptNodes that make up the contents of this EditableScript */
    private List<EditableNode> nodes;

    /** Keeps track of whether any changes have been made to this EditableScript */
    private boolean isModified;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates an instance of an empty {@link EditableScript} with the given
     * {@code dialogueName} and {@code languageCode}. If the provided {@code dialogueName} is {@code
     * null} or the empty {@link String}, the dialogueName will default to {@link
     * Constants#DLB_DEFAULT_DIALOGUE_NAME}.
     *
     * <p>If the provided {@code languageCode} is {@code null} or the empty {@link String}, the
     * languageCode will default to {@link Constants#DLB_DEFAULT_LANGUAGE_CODE}.</p>
     *
     * @param dialogueName the name of this {@link EditableScript}.
     * @param languageCode the language code for this {@link EditableScript}.
     */
    public EditableScript(String dialogueName, String languageCode, StorageSource storageSource) {
        // Set the dialogue name, or use the default value
        if(dialogueName == null || dialogueName.isEmpty()) {
            this.dialogueName = Constants.DLB_DEFAULT_DIALOGUE_NAME;
        } else {
            this.dialogueName = dialogueName;
        }

        // Set the languageCode, or use the default value
        if(languageCode == null || languageCode.isEmpty()) {
            this.languageCode = Constants.DLB_DEFAULT_LANGUAGE_CODE;
        } else {
            this.languageCode = languageCode;
        }

        this.storageSource = storageSource;

        this.nodes = new ArrayList<>();
        this.isModified = false;
    }

    /**
     * Creates an instance of a {@link EditableScript} with the given {@code dialogueName},
     * {@code languageCode} and list of {@link EditableNode}s, representing the contents of this
     * {@link EditableScript}.
     *
     * <p>If the provided {@code dialogueName} is {@code null} or the empty {@link String}, the
     * dialogueName will default to {@link Constants#DLB_DEFAULT_DIALOGUE_NAME}.</p>
     *
     * <p>If the provided {@code languageCode} is {@code null} or the empty {@link String}, the
     * languageCode will default to {@link Constants#DLB_DEFAULT_LANGUAGE_CODE}.</p>
     *
     * @param dialogueName the name of this {@link EditableScript}.
     * @param languageCode the language code for this {@link EditableScript}.
     * @param nodes the {@link EditableNode}s that make up this {@link EditableScript}.
     */
    public EditableScript(String dialogueName, String languageCode, StorageSource storageSource,
                          List<EditableNode> nodes) {
        this(dialogueName, languageCode, storageSource);

        this.nodes = Objects.requireNonNullElseGet(nodes, ArrayList::new);
        for(EditableNode node : this.nodes) {
            node.addPropertyChangeListener(PROPERTY_IS_MODIFIED,this);
        }
        this.isModified = false;
    }

    // ----------------------------------------------------------- //
    // -------------------- Getters & Setters -------------------- //
    // ----------------------------------------------------------- //

    /**
     * Returns the name of this {@link EditableScript} as a String.
     *
     * @return the name of this {@link EditableScript} as a String.
     */
    public String getDialogueName() {
        return dialogueName;
    }

    /**
     * Sets the name of this {@link EditableScript} as a String. If the provided {@code
     * dialogueName} is {@code null} or the empty {@link String}, the dialogueName will default to
     * {@link Constants#DLB_DEFAULT_DIALOGUE_NAME}.
     *
     * @param dialogueName the name of this {@link EditableScript} as a String.
     */
    public void setDialogueName(String dialogueName) {
        if(dialogueName == null || dialogueName.isEmpty()) {
            dialogueName = Constants.DLB_DEFAULT_DIALOGUE_NAME;
        }
        String oldValue = this.dialogueName;
        this.dialogueName = dialogueName;
        this.getPropertyChangeSupport()
                .firePropertyChange(PROPERTY_SCRIPT_DIALOGUE_NAME, oldValue, dialogueName);
    }

    /**
     * Returns the language code for this {@link EditableScript}.
     *
     * @return the language code for this {@link EditableScript}.
     */
    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * Sets the language code for this {@link EditableScript} as a String. If the provided
     * {@code languageCode} is {@code null} or the empty {@link String}, the languageCode will
     * default to {@link Constants#DLB_DEFAULT_LANGUAGE_CODE}.
     *
     * @param languageCode the language code for this {@link EditableScript}.
     */
    public void setLanguageCode(String languageCode) {
        if(languageCode == null || languageCode.isEmpty()) {
           languageCode = Constants.DLB_DEFAULT_LANGUAGE_CODE;
        }
        String oldValue = this.languageCode;
        this.languageCode = languageCode;
        this.getPropertyChangeSupport()
                .firePropertyChange(PROPERTY_SCRIPT_LANGUAGE_CODE, oldValue, languageCode);
    }

    /**
     * Returns the {@link StorageSource} for this {@link EditableScript}, providing a link to the
     * medium where this script has been stored.
     *
     * @return the {@link StorageSource} for this {@link EditableScript}.
     */
    public StorageSource getStorageSource() {
        return this.storageSource;
    }

    /**
     * Returns the List of {@link EditableNode}s that make up this {@link EditableScript}.
     *
     * @return the List of {@link EditableNode}s that make up this {@link EditableScript}.
     */
    public List<EditableNode> getNodes() {
        return nodes;
    }

    /**
     * Sets the List of {@link EditableNode}s that make up this {@link EditableScript}. If a
     * {@code null}-value is provided, the list of script nodes is set to an empty list.
     *
     * @param editableNodes the List of {@link EditableNode}s that make up this {@link
     *                    EditableScript}.
     */
    public void setNodes(List<EditableNode> editableNodes) {
        List<EditableNode> oldNodes = new ArrayList<>(this.nodes);
        for(EditableNode oldNode : oldNodes) {
            oldNode.removePropertyChangeListener(this);
        }
        this.nodes = Objects.requireNonNullElseGet(editableNodes, ArrayList::new);
        for(EditableNode node : this.nodes) {
            node.addPropertyChangeListener(PROPERTY_IS_MODIFIED,this);
        }
        this.getPropertyChangeSupport()
                .firePropertyChange(PROPERTY_SCRIPT_NODES, oldNodes, this.nodes);
    }

    /**
     * Returns whether the contents of this script has been modified (true), or not (false).
     *
     * @return whether the contents of this script has been modified (true), or not (false).
     */
    public boolean isModified() {
        return this.isModified;
    }

    /**
     * Sets whether the contents of this script has been modified (true), or not (false). If set to
     * {@code false}, all of this script's {@link EditableNode}s will also be set to be <em>not</em>
     * modified.
     *
     * @param isModified whether the contents of this script has been modified (true), or not
     *                   (false).
     */
    public void setModified(boolean isModified) {
        boolean oldValue = this.isModified;
        this.isModified = isModified;

        // If this script is set to be _not_ modified, all its nodes are also not modified
        if(!isModified) {
            for(EditableNode editableNode : nodes) {
                editableNode.setModified(false);
            }
        }
        this.getPropertyChangeSupport()
                .firePropertyChange(PROPERTY_IS_MODIFIED,oldValue,isModified);
    }

    // ------------------------------------------------------- //
    // -------------------- Other Methods -------------------- //
    // ------------------------------------------------------- //

    public String getCompleteCode() {
        StringBuilder codeBuilder = new StringBuilder();
        for(EditableNode node : nodes) {
            codeBuilder.append(node.getHeader().getSourceCode());
            codeBuilder.append(System.lineSeparator());
            codeBuilder.append(Constants.DLB_HEADER_SEPARATOR);
            codeBuilder.append(System.lineSeparator());
            codeBuilder.append(node.getBody().getSourceCode());
            codeBuilder.append(System.lineSeparator());
            codeBuilder.append(Constants.DLB_NODE_SEPARATOR);
            codeBuilder.append(System.lineSeparator());
        }
        return codeBuilder.toString();
    }

    /**
     * Completely updates the contents of this {@link EditableScript} based on the given
     * {@code code}.
     *
     * @param code a String representing the script code for this {@link EditableScript}.
     */
    public void setCompleteCode(String code) {

        // First, remove the old list of nodes, unregistering property change listeners,
        // and finally informing listeners of this change.
        List<EditableNode> oldNodes = new ArrayList<>(this.nodes);
        for(EditableNode node : this.nodes) {
            node.removePropertyChangeListener(this);
        }
        this.nodes = new ArrayList<>();
        this.getPropertyChangeSupport().firePropertyChange(PROPERTY_SCRIPT_NODES, oldNodes, this.nodes);

        // Next, use the EditableScriptParser to re-generate the contents of this script
        List<String> codeLines = code.lines().toList();
        EditableScriptParser.setContents(codeLines,this);
        this.setModified(true);
    }

    public List<EditableNode> getNodesByTitle(String title) {
        List<EditableNode> foundNodes = new ArrayList<>();
        for(EditableNode node : nodes) {
            if(node.getTitle().equals(title)) {
                foundNodes.add(node);
            }
        }
        return foundNodes;
    }

    /**
     * Adds the given {@link EditableNode} to the list of {@link EditableNode}s for this {@link
     * EditableScript} if a non-{@code null} value is provided.
     *
     * @param node the {@link EditableNode} to add to this {@link EditableScript}.
     */
    public void addNode(EditableNode node) {
        if(node != null) {
            List<EditableNode> oldNodes = new ArrayList<>(this.nodes);
            this.nodes.add(node);
            node.addPropertyChangeListener(PROPERTY_IS_MODIFIED,this);
            this.getPropertyChangeSupport()
                    .firePropertyChange(PROPERTY_SCRIPT_NODES, oldNodes, this.nodes);
        }
    }

    public void removeNode(EditableNode node) {
        if(node != null) {
            List<EditableNode> oldNodes = new ArrayList<>(this.nodes);
            node.removePropertyChangeListener(this);
            this.nodes.remove(node);
            this.getPropertyChangeSupport()
                    .firePropertyChange(PROPERTY_SCRIPT_NODES, oldNodes, this.nodes);
        }
    }

    /**
     * Returns a human-readable String representation of this {@link EditableScript}.
     *
     * @return a human-readable String representation of this {@link EditableScript}.
     */
    public String toString() {
        return "EditableScript with name '" + dialogueName
                + "' in language '" + languageCode + "' and " + nodes.size() + " nodes.";
    }

    /**
     * An {@link EditableScript} listens to PROPERTY_IS_MODIFIED events of all of its EditableNodes.
     * If any of its nodes is modified, this script is also set as modified.
     *
     * @param event A PropertyChangeEvent object describing the event source and the property that
     *              has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(event.getPropertyName().equals(PROPERTY_IS_MODIFIED)) {
            boolean modified = (Boolean) event.getNewValue();
            if(modified) {
                this.setModified(true);
            }
        }
    }

}
