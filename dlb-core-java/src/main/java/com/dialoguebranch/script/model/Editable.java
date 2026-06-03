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

import com.dialoguebranch.script.warning.ParserWarning;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * Any class extending the {@link Editable} object will be able to report changes in its data model
 * through the {@link PropertyChangeSupport} mechanism, using a shared set of {@code propertyNames}.
 *
 * <p>In addition, an editable object must define a way for it to be parsed, and stores potential
 * warnings that resulted from the latest parse.</p>
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public abstract class Editable {

    public Editable() {
        parserWarnings = new ArrayList<>();
    }

    // ------------------------------------------------------------ //
    // -------------------- Parsing & Warnings -------------------- //
    // ------------------------------------------------------------ //

    /** Any Editable* Object may be parsed in a fault-tolerant way, generating ParserWarnings */
    private List<ParserWarning> parserWarnings;

    /**
     * Add a {@link ParserWarning} to the list of {@link ParserWarning}s for this {@link Editable}.
     *
     * @param parserWarning the {@link ParserWarning} to add.
     */
    public void addParserWarning(ParserWarning parserWarning) {
        parserWarnings.add(parserWarning);
    }

    /**
     * Clear the list of {@link ParserWarning}s for this {@link Editable} (e.g., before starting a
     * new parse of the implementing object).
     */
    public void clearWarnings() {
        this.parserWarnings = new ArrayList<>();
    }

    /**
     * Returns the list of {@link ParserWarning}s for this {@link Editable}.
     *
     * @return the list of {@link ParserWarning}s for this {@link Editable}.
     */
    public List<ParserWarning> getParserWarnings() {
        return parserWarnings;
    }

    // ----------------------------------------------------------------- //
    // -------------------- Property Change Support -------------------- //
    // ----------------------------------------------------------------- //

    /** Generic event name indicating that the contents of the object have been modified */
    public static final String PROPERTY_IS_MODIFIED = "isModified";

    /** Event name indicating that the dialogue name within a script is updated */
    public static final String PROPERTY_SCRIPT_DIALOGUE_NAME = "dialogueName";

    /** Event name indicating that the language code within a script is updated */
    public static final String PROPERTY_SCRIPT_LANGUAGE_CODE = "languageCode";

    /** Event name indicating that the list of nodes within a script is updated */
    public static final String PROPERTY_SCRIPT_NODES = "nodes";

    /** Event name indicating that the metadata within a project has been updated */
    public static final String PROPERTY_PROJECT_METADATA = "metaData";

    /** Event name indicating that the list of available scripts within a project is updated */
    public static final String PROPERTY_PROJECT_AVAILABLE_SCRIPTS = "availableScripts";

    /** The PropertyChangeSupport object used for informing listeners of changes */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Returns the {@link PropertyChangeSupport} object associated with this {@link Editable}.
     *
     * @return the {@link PropertyChangeSupport} object associated with this {@link Editable}.
     */
    public PropertyChangeSupport getPropertyChangeSupport() {
        return pcs;
    }

    /**
     * Adds a {@link PropertyChangeListener} to the list of listeners for this object.
     *
     * @param listener the {@link PropertyChangeListener} to add.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    /**
     * Adds a {@link PropertyChangeListener} to the list of listeners for this object that only
     * listens to the given {@code propertyName}.
     *
     * @param propertyName the name of the property for which changes to listen.
     * @param listener the {@link PropertyChangeListener} to add.
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes the given {@link PropertyChangeListener} from the list of listeners for this object.
     *
     * @param listener the {@link PropertyChangeListener} to remove.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

}
