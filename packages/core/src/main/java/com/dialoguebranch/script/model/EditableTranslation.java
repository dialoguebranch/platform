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

import com.dialoguebranch.i18n.SourceTranslatable;
import com.dialoguebranch.i18n.TranslationFile;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link EditableTranslation} is an object representation of a JSON file that contains
 * translations for a single Dialogue Branch script. The body of an {@link EditableTranslation}
 * consists of a mapping from speaker names to a map of {term,translation}-pairs, where the
 * "speaker name" for the special 'user' case (i.e. for replies) is defined in {@link
 * SourceTranslatable#USER}, e.g.:
 *
 * <pre>
 * {
 *   "speaker1" : {
 *     "term1" : "translation1",
 *     "term2" : "translation2"
 *   },
 *   "speaker2" : {
 *     "term3" : "translation3",
 *     "term4" : "translation4"
 *   }
 *   "_user" : {
 *     "term5" : "translation5",
 *     "term6" : "translation6"
 *   }
 * }</pre>
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Dennis Hofs (Roessingh Research and Development)
 */
public class EditableTranslation {

    /** The name of the dialogue to which these translations belong (unique within a project) */
    private final String dialogueName;

    /** An object that contains information on where/how this EditableTranslation is stored */
    private final StorageSource storageSource;

    /** A mapping from speaker name to {term-translation}-pairs */
    private final Map<String, Map<String,String>> translations;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    public EditableTranslation(String dialogueName, StorageSource storageSource) {
        this.dialogueName = dialogueName;
        this.storageSource = storageSource;
        this.translations = new HashMap<>();
    }

    // ----------------------------------------------------------- //
    // -------------------- Getters & Setters -------------------- //
    // ----------------------------------------------------------- //

    public String getDialogueName() {
        return dialogueName;
    }

    public StorageSource getStorageSource() {
        return storageSource;
    }

    public Map<String, Map<String, String>> getTranslations() {
        return translations;
    }

    // -------------------------------------------------------- //
    // -------------------- Public Methods -------------------- //
    // -------------------------------------------------------- //

    public void addTerm(String speakerName, String term, String translation) {
        if(translations.containsKey(speakerName)) {
            Map<String,String> terms = translations.get(speakerName);
            terms.put(term,translation);
        } else {
            Map<String,String> terms = new HashMap<>();
            terms.put(term,translation);
            translations.put(speakerName,terms);
        }
    }

}
