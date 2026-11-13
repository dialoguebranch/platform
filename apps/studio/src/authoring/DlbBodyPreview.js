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
 * The plain text of a node's body, for a preview on its card in the graph editor (#200): the
 * reply links are left out (the graph draws them), `<<...>>` command tags are left out but the text
 * inside an `<<if>>` or `<<random>>` stays, variables stay as written, and whitespace is collapsed.
 * A long text is cut at a word, with an ellipsis. It's a glimpse of the node, not how it runs.
 *
 * @param {?string} body the node's body, as the Web Service returns it.
 * @param {number} [maxLength] the longest preview, before the ellipsis.
 * @returns {string} the preview, or an empty string when the body has no text.
 */
export function bodyPreview(body, maxLength = 90) {
    if (!body) return '';
    const text = body
        .replace(/\[\[.*?]]/gs, ' ')
        .replace(/<<.*?>>/gs, ' ')
        .replace(/\s+/g, ' ')
        .trim()
        // a command between a word and its punctuation leaves a space before it
        .replace(/ ([.,;:!?])/g, '$1');
    if (text.length <= maxLength) return text;
    const cut = text.slice(0, maxLength + 1);
    const lastSpace = cut.lastIndexOf(' ');
    return (lastSpace > 0 ? cut.slice(0, lastSpace) : text.slice(0, maxLength)).trimEnd() + '…';
}
