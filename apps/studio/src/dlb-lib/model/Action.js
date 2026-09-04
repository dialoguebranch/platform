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
 * A resolved Dialogue Branch action, as produced by an `<<action>>` command in a `.dlb` script.
 * The Web Service resolves any `$variable` references in the value and parameters before sending
 * it, so the fields here are plain strings ready for a client to act on.
 *
 * An action appears either as an `ACTION` segment inside an agent statement, or in the `actions`
 * list of a reply (from `[[text|target|<<action ...>>]]`).
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class Action {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates an Action.
     *
     * @param {string} type one of `"link"`, `"image"`, `"video"` or `"generic"`.
     * @param {string} value the resolved value — a URL for `link`/`video`, a resource reference
     * for `image`, or a client-defined token for `generic`.
     * @param {Object<string,string>} [parameters] optional extra parameters (e.g. `text` on a
     * link, `delay`/`page` on a generic action), keyed by name. Defaults to an empty object.
     */
    constructor(type, value, parameters = {}) {
        this._type = type;
        this._value = value;
        this._parameters = parameters ?? {};
    }

    /**
     * Creates an Action from the JSON form the Web Service sends (a resolved `DialogueAction`:
     * `{ type, value, parameters }`).
     *
     * @param {Object} json the action object from the API response.
     * @returns {Action} the parsed Action.
     */
    static fromJSON(json) {
        return new Action(json.type, json.value, json.parameters ?? {});
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    get type() {
        return this._type;
    }

    set type(type) {
        this._type = type;
    }

    get value() {
        return this._value;
    }

    set value(value) {
        this._value = value;
    }

    get parameters() {
        return this._parameters;
    }

    set parameters(parameters) {
        this._parameters = parameters ?? {};
    }

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------

    /**
     * Returns the value of the given optional parameter, or `null` if it is not present.
     *
     * @param {string} name the parameter name.
     * @returns {string|null} the parameter value, or `null`.
     */
    parameter(name) {
        return Object.prototype.hasOwnProperty.call(this._parameters, name)
            ? this._parameters[name]
            : null;
    }

    toString() {
        return "Action{type: " + this._type + ", value: " + this._value
            + ", parameters: " + JSON.stringify(this._parameters) + "}";
    }

}
