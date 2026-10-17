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
 * Reserved node-header tags with a special meaning, matching
 * {@code DialogueBranchConstants.DLB_RESERVED_HEADER_TAGS} in packages/core (note: "colorId", not
 * "colorID").
 */
export const RESERVED_HEADER_TAGS = ['title', 'speaker', 'position', 'colorId', 'comment'];

/**
 * The speaker name of the user in translation files, reserved so no node can have it as its
 * speaker (#383), matching {@code DialogueBranchConstants.DLB_USER_SPEAKER} in packages/core.
 */
export const USER_SPEAKER = '_user';

/**
 * Turns a node's header tags, as the Web Service sends them (an ordered list of `{ key, value }`,
 * see `DialogueBranchAuthoringClient.listDraftNodes`), into an ordered Map of tag name to value,
 * for looking up and changing tags. If a key appears more than once, its first occurrence wins.
 *
 * @param {{key: string, value: string}[]} tags the node's header tags.
 * @returns {Map<string,string>} the tags, in order.
 */
export function tagsToMap(tags) {
    const map = new Map();
    for (const { key, value } of tags ?? []) {
        if (!map.has(key)) map.set(key, value);
    }
    return map;
}

/**
 * Turns an ordered Map of tag name to value back into the list of `{ key, value }` tags the Web
 * Service takes (see `DialogueBranchAuthoringClient.updateDraftNode`), in Map order.
 *
 * @param {Map<string,string>} map the tags.
 * @returns {{key: string, value: string}[]} the tags, in order.
 */
export function mapToTags(map) {
    return [...map.entries()].map(([key, value]) => ({ key, value }));
}

/**
 * Reads the `position: x,y` tag (if present and well-formed) into a `{ x, y }` pair. Missing or
 * malformed values default to 0.
 *
 * @param {Map<string,string>} tags the header tags (see {@link tagsToMap}).
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
