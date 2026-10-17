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
 * A dialogue's preamble is stored as `//` comment lines (the comment block at the start of its
 * script). Studio lets authors edit it as plain text instead: these convert between the two.
 */

/**
 * Turns a stored preamble into plain text for editing: each comment line without its leading
 * `//` and the one space after it. Further indentation is kept.
 *
 * @param {string} preamble the stored preamble (`//` lines joined with `\n`), or empty.
 * @returns {string} the plain text, one line per comment line.
 */
export function preambleToText(preamble) {
    if (!preamble) return '';
    return preamble.split('\n').map((line) => line.replace(/^\s*\/\/ ?/, '')).join('\n');
}

/**
 * Turns edited plain text back into a preamble: each line becomes a `// ` comment line, and an
 * empty line a bare `//`, so blank lines between paragraphs are kept. Empty lines at the start and
 * end are left out.
 *
 * @param {string} text the plain text.
 * @returns {string} the preamble (`//` lines joined with `\n`), or `''` for none.
 */
export function textToPreamble(text) {
    const lines = (text ?? '').split(/\r\n|\r|\n/);
    const first = lines.findIndex((line) => line.trim() !== '');
    if (first === -1) return '';
    const last = lines.findLastIndex((line) => line.trim() !== '');
    return lines.slice(first, last + 1)
        .map((line) => `// ${line}`.trimEnd())
        .join('\n');
}
