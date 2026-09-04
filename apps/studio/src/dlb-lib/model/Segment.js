/* @license
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import { Action } from './Action.js';

/**
 * One part of a dialogue statement. The Web Service sends a statement as an ordered list of
 * segments; a client renders them in order. There are three kinds, distinguished by {@link type}:
 *
 * - `"TEXT"` — a run of (already `$variable`-resolved) text, in {@link text}.
 * - `"INPUT"` — a resolved `<<input>>` command, in {@link inputType} / {@link description} /
 *   {@link parameters}. Only ever appears inside a reply's statement.
 * - `"ACTION"` — a resolved `<<action>>` command, in {@link action}.
 *
 * The constructor keeps its original `(type, text)` shape for the common `TEXT` case and for
 * test fixtures; use {@link Segment.fromJSON} to build a segment of any kind from an API
 * response.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class Segment {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates a segment. For a `TEXT` segment, pass the text. For `INPUT` / `ACTION`, prefer
     * {@link Segment.fromJSON}; the extra fields can also be set afterwards via their setters.
     *
     * @param {string} type one of `"TEXT"`, `"INPUT"`, `"ACTION"`.
     * @param {string} [text] the text, for a `TEXT` segment.
     */
    constructor(type, text) {
        this._type = type;
        this._text = text;

        // INPUT-only
        this._inputType = null;
        this._description = null;
        this._parameters = {};

        // ACTION-only
        this._action = null;
    }

    /**
     * Builds a segment from the JSON form the Web Service sends, dispatching on its
     * `segmentType` field.
     *
     * @param {Object} json a statement segment from the API (`{ segmentType, ... }`).
     * @returns {Segment} the parsed segment.
     */
    static fromJSON(json) {
        const type = json.segmentType;
        if (type === 'INPUT') {
            // The Web Service flattens an input command's parameters onto the segment object
            // (see DialogueStatement.InputSegmentSerializer) — everything that is not
            // segmentType / inputType / description is a parameter (e.g. variableName, min,
            // max, granularityMinutes, options).
            const { segmentType, inputType, description, ...parameters } = json;
            const segment = new Segment('INPUT', '');
            segment._inputType = inputType ?? null;
            segment._description = description ?? null;
            segment._parameters = parameters;
            return segment;
        }
        if (type === 'ACTION') {
            const segment = new Segment('ACTION', '');
            segment._action = json.action ? Action.fromJSON(json.action) : null;
            return segment;
        }
        return new Segment('TEXT', json.text);
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    set type(type) {
        this._type = type;
    }

    get type() {
        return this._type;
    }

    set text(text) {
        this._text = text;
    }

    get text() {
        return this._text;
    }

    /** The input type (`"text"`, `"longtext"`, `"email"`, `"numeric"`, `"time"`, `"set"`), or `null`. */
    get inputType() {
        return this._inputType;
    }

    set inputType(inputType) {
        this._inputType = inputType;
    }

    /** An optional hint / accessibility label for an `INPUT` segment, or `null`. */
    get description() {
        return this._description;
    }

    set description(description) {
        this._description = description;
    }

    /** The resolved parameters of an `INPUT` segment (name → value), or `{}`. */
    get parameters() {
        return this._parameters;
    }

    set parameters(parameters) {
        this._parameters = parameters ?? {};
    }

    /** The {@link Action} of an `ACTION` segment, or `null`. */
    get action() {
        return this._action;
    }

    set action(action) {
        this._action = action;
    }

}
