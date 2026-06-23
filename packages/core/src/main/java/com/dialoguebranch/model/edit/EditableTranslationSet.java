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

package com.dialoguebranch.model.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * A container that groups a set of {@link EditableTranslation} objects belonging to a single
 * language or context within an {@link EditableProject}.
 *
 * @author Harm op den Akker
 */
public class EditableTranslationSet {

    private List<EditableTranslation> editableTranslations;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /** Creates an empty {@link EditableTranslationSet}. */
    public EditableTranslationSet() {
        editableTranslations = new ArrayList<>();
    }

    /**
     * Creates an {@link EditableTranslationSet} pre-populated with the given list of translations.
     * @param editableTranslations the initial list of {@link EditableTranslation}s.
     */
    public EditableTranslationSet(List<EditableTranslation> editableTranslations) {
        this.editableTranslations = editableTranslations;
    }

    // ----------------------------------------------------------- //
    // -------------------- Getters & Setters -------------------- //
    // ----------------------------------------------------------- //

    /**
     * Returns the list of {@link EditableTranslation}s in this set.
     * @return the list of editable translations.
     */
    public List<EditableTranslation> getEditableTranslations() {
        return editableTranslations;
    }

    /**
     * Sets the list of {@link EditableTranslation}s in this set.
     * @param editableTranslations the new list of editable translations.
     */
    public void setEditableTranslations(List<EditableTranslation> editableTranslations) {
        this.editableTranslations = editableTranslations;
    }

    // -------------------------------------------------------- //
    // -------------------- Public Methods -------------------- //
    // -------------------------------------------------------- //

    /**
     * Adds the given {@link EditableTranslation} to this set.
     * @param editableTranslation the translation to add.
     */
    public void addEditableTranslation(EditableTranslation editableTranslation) {
        editableTranslations.add(editableTranslation);
    }

}
