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

/**
 * A SupportedVariable is a variable the project's configured External Variable Service owns, as
 * returned by the Web Service's `GET /variables/list-supported` end-point: its name, and the
 * optional description and hints the service gave for it.
 *
 * The hints (`type`, `values` and `readOnly`) are for authors only: neither the Web Service nor
 * the core library enforces them. A hint the service didn't give is `null`, never a default, so
 * `readOnly === null` means "not said", not "writable".
 */
export class SupportedVariable {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * @param {string} name The variable's name (without the leading `$`).
     * @param {?string} description What the variable holds, or `null`.
     * @param {?string} type `number`, `string` or `boolean`, or `null`.
     * @param {?Array} values The values the variable may take, or `null` if any value is allowed.
     * @param {?boolean} readOnly Whether the service doesn't accept changes from a dialogue, or
     * `null`.
     */
    constructor(name, description, type, values, readOnly) {
        this._name = name;
        this._description = description;
        this._type = type;
        this._values = values;
        this._readOnly = readOnly;
    }

    /**
     * Builds a SupportedVariable from the JSON form the Web Service sends.
     *
     * @param {Object} json `{ name, description?, type?, values?, readOnly? }`.
     * @returns {SupportedVariable} The parsed variable.
     */
    static fromJSON(json) {
        return new SupportedVariable(json.name, json.description ?? null, json.type ?? null,
            Array.isArray(json.values) ? json.values : null,
            typeof json.readOnly === 'boolean' ? json.readOnly : null);
    }

    // -----------------------------
    // ---------- Getters ----------
    // -----------------------------

    /**
     * Returns the variable's name.
     * @returns {string} the name, without the leading `$`.
     */
    get name() {
        return this._name;
    }

    /**
     * Returns what the variable holds, as the service described it.
     * @returns {?string} the description, or `null`.
     */
    get description() {
        return this._description;
    }

    /**
     * Returns the variable's type, as the service declared it.
     * @returns {?string} `number`, `string` or `boolean`, or `null` if the service didn't say.
     */
    get type() {
        return this._type;
    }

    /**
     * Returns the values the variable may take.
     * @returns {?Array} the allowed values, or `null` if any value is allowed.
     */
    get values() {
        return this._values;
    }

    /**
     * Returns whether the service computes or controls the variable and doesn't accept changes
     * from a dialogue. A dialogue can still set it, but that only changes Dialogue Branch's own
     * copy until the service's value replaces it.
     * @returns {?boolean} `true` or `false`, or `null` if the service didn't say.
     */
    get readOnly() {
        return this._readOnly;
    }

}
