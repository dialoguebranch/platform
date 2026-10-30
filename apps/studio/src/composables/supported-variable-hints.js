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

// Pure helpers behind the Variable Browser's display of the hints an External Variable Service
// gives for its supported variables (#434): `type`, `values` and `readOnly`, as client-js's
// SupportedVariable carries them. A hint the service didn't give is null and is simply not shown,
// never guessed. No Vue, no network, so each rule is unit-testable on its own (see
// supported-variable-hints.spec.js).

/**
 * Returns the lines of a supported variable's tooltip in the "Used" list: that the service
 * supports it, then its description and each hint it gave.
 *
 * @param {?{description: ?string, type: ?string, values: ?Array, readOnly: ?boolean}} supported
 * The variable as the service reported it, or null if it isn't one of its variables.
 * @returns {string[]} the lines, empty for a variable the service doesn't report.
 */
export function supportedVariableLines(supported) {
    if (!supported) return [];
    const lines = ['Supported by the External Variable Service'];
    if (supported.description) lines.push(supported.description);
    if (supported.type) lines.push('Type: ' + supported.type);
    if (Array.isArray(supported.values) && supported.values.length > 0)
        lines.push('Allowed values: ' + supported.values.map(String).join(', '));
    if (supported.readOnly === true)
        lines.push('Read-only: the service doesn\'t accept changes from a dialogue');
    else if (supported.readOnly === false)
        lines.push('Dialogues may change it');
    return lines;
}

/**
 * Returns what changing a read-only variable from the Variable Browser does, for the lock shown
 * next to it in "Values" (or "Test values").
 *
 * @param {boolean} testing whether the list shows the test copy of the variables.
 * @returns {string} the explanation.
 */
export function readOnlyNote(testing) {
    return testing
        ? 'Read-only: the External Variable Service doesn\'t accept changes to this variable. '
            + 'Changing it here only changes the test values.'
        : 'Read-only: the External Variable Service doesn\'t accept changes to this variable. '
            + 'A change made here is replaced by the service\'s value at the next dialogue start.';
}
