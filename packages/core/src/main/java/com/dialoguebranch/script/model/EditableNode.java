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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * An {@link EditableNode} represents a node in Dialogue Branch script that may or may not be
 * correctly parseable according to the Dialogue Branch Language Specification. This class may be
 * used to e.g. represent a node during editing, and it is extremely fault-tolerant.
 *
 * <p>A {@link EditableNode} is a simple container class that contains an {@link EditableHeader} and
 * an {@link EditableBody}, both of which can be empty, but never {@code null}.</p>
 *
 * <p>This class informs registered property change listeners of changes in its content using the
 * {@link PropertyChangeSupport} mechanism.</p>
 *
 * @see Editable
 * @see PropertyChangeListener
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class EditableNode extends Editable implements PropertyChangeListener {

    /** The EditableScript that this EditableNode belongs to */
    private final EditableScript editableScript;

    /** The header part of this node */
    private EditableHeader header;

    /** The body part of this node */
    private EditableBody body;

    /** Stores whether any changes have been made to this node */
    private boolean isModified;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates an instance of an empty {@link EditableNode} that belongs to the given {@link
     * EditableScript}.
     *
     * @param editableScript the EditableScript that this EditableNode belongs to.
     */
    public EditableNode(EditableScript editableScript) {
        this.editableScript = editableScript;
        this.header = new EditableHeader(this);
        this.body = new EditableBody(this);
        this.isModified = false;

        this.header.addPropertyChangeListener(this);
        this.body.addPropertyChangeListener(this);
    }

    /**
     * Creates an instance of a {@link EditableNode} that belongs to the given {@link
     * EditableScript} with a given {@code header} and {@code body}.
     *
     * @param editableScript the EditableScript that this EditableNode belongs to.
     * @param header the {@link EditableHeader} representing the header of this {@link EditableNode}.
     * @param body the {@link EditableBody} representing the body of this {@link EditableNode}.
     */
    public EditableNode(EditableScript editableScript, EditableHeader header, EditableBody body) {
        this.editableScript = editableScript;
        if(header == null) {
            header = new EditableHeader(this);
        }
        this.header = header;

        if(body == null) {
            body = new EditableBody(this);
        }
        this.body = body;

        this.isModified = false;

        this.header.addPropertyChangeListener(this);
        this.body.addPropertyChangeListener(this);
    }

    // ----------------------------------------------------------- //
    // -------------------- Getters & Setters -------------------- //
    // ----------------------------------------------------------- //

    /**
     * Returns the EditableScript that this EditableNode belongs to.
     * @return the EditableScript that this EditableNode belongs to.
     */
    public EditableScript getEditableScript() {
        return this.editableScript;
    }

    /**
     * Returns the {@link EditableHeader} associated with this {@link EditableNode}.
     *
     * @return the {@link EditableHeader} associated with this {@link EditableNode}.
     */
    public EditableHeader getHeader() {
        return header;
    }

    /**
     * Sets the {@link EditableHeader} associated with this {@link EditableNode}.
     *
     * @param header the {@link EditableHeader} associated with this {@link EditableNode}.
     */
    public void setHeader(EditableHeader header) {
        this.header.removePropertyChangeListener(this);
        if(header == null) {
            header = new EditableHeader(this);
        }
        this.header = header;
        this.header.addPropertyChangeListener(this);
    }

    /**
     * Returns the {@link EditableBody} associated with this {@link EditableNode}.
     *
     * @return the {@link EditableBody} associated with this {@link EditableNode}.
     */
    public EditableBody getBody() {
        return body;
    }

    /**
     * Sets the {@link EditableBody} associated with this {@link EditableNode}.
     *
     * @param body the {@link EditableBody} associated with this {@link EditableNode}.
     */
    public void setBody(EditableBody body) {
        this.body.removePropertyChangeListener(this);
        if(body == null) {
            body = new EditableBody(this);
        }
        this.body = body;
        this.body.addPropertyChangeListener(this);
    }

    /**
     * Returns whether the contents of this node has been modified (true), or not (false).
     *
     * @return whether the contents of this node has been modified (true), or not (false).
     */
    public boolean isModified() {
        return this.isModified;
    }

    /**
     * Sets whether the contents of this body has been modified (true), or not (false). If set to
     * {@code false}, its associated {@link EditableBody} and {@link EditableHeader} will also be
     * set to be <em>not</em> modified.
     *
     * @param isModified whether the contents of this body has been modified (true), or not (false).
     */
    public void setModified(boolean isModified) {
        boolean oldValue = this.isModified;
        this.isModified = isModified;

        // If this node is set to be _not_ modified, its header and body are also not
        if(!isModified) {
            this.header.setModified(false);
            this.body.setModified(false);
        }
        this.getPropertyChangeSupport()
                .firePropertyChange(PROPERTY_IS_MODIFIED,oldValue,isModified);
    }

    // ------------------------------------------------------- //
    // -------------------- Other Methods -------------------- //
    // ------------------------------------------------------- //

    /**
     * Returns the title of this Node, or an empty String.
     *
     * @return the title of this Node, or the empty String.
     */
    public String getTitle() {
        return this.header.getTitle();
    }

    /**
     * Returns the speaker of this Node, or an empty String.
     *
     * @return the speaker of this Node, or an empty String.
     */
    public String getSpeaker() {
        return this.header.getSpeaker();
    }

    /**
     * Returns a human-readable String representation of this {@link EditableNode}.
     *
     * @return a human-readable String representation of this {@link EditableNode}.
     */
    public String toString() {
        return header.getSourceCode() +
                System.lineSeparator() +
                Constants.DLB_HEADER_SEPARATOR +
                System.lineSeparator() +
                body.getSourceCode() +
                System.lineSeparator() +
                Constants.DLB_NODE_SEPARATOR +
                System.lineSeparator();
    }

    /**
     * Returns a human-readable String summary of this {@link EditableNode}.
     *
     * @return a human-readable String summary of this {@link EditableNode}.
     */
    public String toStringSummary() {
        return "EditableNode [title: '" + this.getTitle() + "']"
                + " with " + this.getBody().getSourceCode().lines().count() + " lines of body"
                + " and " + this.getHeader().getTags().size() + " header tags.";
    }

    // ------------------------------------------------------------------- //
    // -------------------- Property Change Listeners -------------------- //
    // ------------------------------------------------------------------- //

    /**
     * An {@link EditableNode} listens to changes in its {@link EditableBody} and {@link
     * EditableHeader}. If either of these trigger an event stating that they have been modified
     * ({@code isModified} is {@code true}) it means this node should be considered to have been
     * modified.
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
