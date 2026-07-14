/* @license
 *
 *                Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

/**
 * Reserved node-header tags with a special meaning, matching
 * {@code DialogueBranchConstants.DLB_RESERVED_HEADER_TAGS} in packages/core (note: "colorId", not
 * "colorID").
 */
export const RESERVED_HEADER_TAGS = ['title', 'speaker', 'position', 'colorId'];

/**
 * Parses a raw `.dlb` node header (the block of `key: value` lines above the `---` separator)
 * into an ordered Map of tag name to value. Mirrors EditableHeaderParser.java's semantics exactly:
 * for each line, a trailing `//` comment is stripped, the *first* `:` splits key from value (both
 * trimmed), lines without a `:` or with an empty key are skipped, and if a key appears more than
 * once, the first occurrence wins (later duplicates are ignored).
 *
 * A JS Map is used (rather than a plain object) so insertion order is preserved when the tags are
 * serialized back — an improvement over the Java model's HashMap-backed, unordered tags.
 *
 * @param {string} sourceCode the raw header source.
 * @returns {Map<string,string>} the parsed tags, in the order they first appeared.
 */
export function parseHeaderTags(sourceCode) {
    const tags = new Map();
    if (!sourceCode) return tags;

    for (const rawLine of sourceCode.split('\n')) {
        const commentIndex = rawLine.indexOf('//');
        const line = commentIndex >= 0 ? rawLine.slice(0, commentIndex) : rawLine;
        const sep = line.indexOf(':');
        if (sep < 0) continue;
        const key = line.slice(0, sep).trim();
        const value = line.slice(sep + 1).trim();
        if (!key) continue;
        if (tags.has(key)) continue; // first occurrence wins
        tags.set(key, value);
    }
    return tags;
}

/**
 * Serializes a Map of tag name to value back into a raw `.dlb` node header, one `key: value` line
 * per entry, in Map iteration order.
 *
 * @param {Map<string,string>} tags the tags to serialize.
 * @returns {string} the raw header source.
 */
export function serializeHeaderTags(tags) {
    return [...tags.entries()].map(([key, value]) => `${key}: ${value}`).join('\n');
}

/**
 * Parses the `position: x,y` tag (if present and well-formed) into a `{ x, y }` pair. Matches
 * EditableHeader.getX()/getY(): missing or malformed values default to 0.
 *
 * @param {Map<string,string>} tags the parsed header tags.
 * @returns {{x: number, y: number}} the node's canvas position.
 */
export function getPosition(tags) {
    const raw = tags.get('position');
    if (!raw) return { x: 0, y: 0 };
    const parts = raw.split(',');
    if (parts.length !== 2) return { x: 0, y: 0 };
    const x = parseInt(parts[0], 10);
    const y = parseInt(parts[1], 10);
    return { x: Number.isNaN(x) ? 0 : x, y: Number.isNaN(y) ? 0 : y };
}

/**
 * Sets the `position: x,y` tag on a tags Map (mutates and returns it), preserving every other
 * tag and its original order.
 *
 * @param {Map<string,string>} tags the tags to update.
 * @param {number} x the new x-coordinate.
 * @param {number} y the new y-coordinate.
 * @returns {Map<string,string>} the same Map, for chaining.
 */
export function setPosition(tags, x, y) {
    tags.set('position', `${Math.round(x)},${Math.round(y)}`);
    return tags;
}
