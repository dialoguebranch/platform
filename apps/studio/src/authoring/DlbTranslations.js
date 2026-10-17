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

// Helpers for a dialogue's translation file content, as the Translation Editor holds it per
// language: { [speaker]: { [sourceText]: translation, [contexts]: { … } } }. A top-level key is a
// speaker (the user's replies are "_user"); a nested object is a group of gender contexts, such as
// "female_addressee", whose entries still belong to that speaker. An entry matches a translatable
// term on its speaker and its whitespace-normalised source text, the way the Translator does
// (#377).

/**
 * The text with leading and trailing whitespace removed and every run of whitespace inside it
 * replaced by one space, as the Web Service lists translatable terms.
 *
 * @param {string} text
 * @returns {string}
 */
export function normalizeText(text) {
    return text.trim().split(/\s+/).filter(Boolean).join(' ');
}

function termKey(speaker, text) {
    return speaker + '\n' + normalizeText(text);
}

/**
 * Every entry of one language's translation content, in file order.
 *
 * @param {Object} content one language's translation content.
 * @returns {{speaker: string, contexts: string[], text: string, translation: string}[]} each
 * entry's speaker, the keys of the context groups it is nested in (empty for none), its source
 * text as stored, and its translation.
 */
export function listEntries(content) {
    const entries = [];
    const walk = (speaker, contexts, group) => {
        for (const [key, value] of Object.entries(group ?? {})) {
            if (value !== null && typeof value === 'object') walk(speaker, [...contexts, key], value);
            else entries.push({ speaker, contexts, text: key, translation: String(value) });
        }
    };
    for (const [speaker, group] of Object.entries(content ?? {})) {
        if (group !== null && typeof group === 'object') walk(speaker, [], group);
    }
    return entries;
}

/**
 * The entries of one language's translation content that match no translatable term of the
 * dialogue on speaker and source text (#375). The Translator never uses them.
 *
 * @param {Object} content one language's translation content.
 * @param {{speaker: string, term: string}[]} terms the dialogue's translatable terms.
 * @returns {{speaker: string, contexts: string[], text: string, translation: string}[]}
 */
export function findStaleEntries(content, terms) {
    const known = new Set(terms.map((t) => termKey(t.speaker, t.term)));
    return listEntries(content).filter((entry) => !known.has(termKey(entry.speaker, entry.text)));
}

/**
 * A copy of one language's translation content without the given entries (as returned by
 * {@link listEntries}), and without the groups that leaves empty.
 *
 * @param {Object} content one language's translation content.
 * @param {{speaker: string, contexts: string[], text: string}[]} entries
 * @returns {Object}
 */
export function withoutEntries(content, entries) {
    const removed = new Set(entries.map((e) => JSON.stringify([e.speaker, ...e.contexts, e.text])));
    const prune = (path, group) => {
        const result = {};
        for (const [key, value] of Object.entries(group)) {
            if (value !== null && typeof value === 'object') {
                const nested = prune([...path, key], value);
                if (Object.keys(nested).length > 0) result[key] = nested;
            } else if (!removed.has(JSON.stringify([...path, key]))) {
                result[key] = value;
            }
        }
        return result;
    };
    return prune([], content ?? {});
}

function groupAt(content, path) {
    let group = content ?? {};
    for (const key of path) {
        const next = group[key];
        if (next === null || typeof next !== 'object') return null;
        group = next;
    }
    return group;
}

/**
 * The source text under which a term's translation is stored in a speaker's group (or a context
 * group nested in it), matching on whitespace-normalised text (#386): the term itself if stored
 * that way, otherwise a stored text that differs from it only in whitespace.
 *
 * @param {Object} content one language's translation content.
 * @param {string} speaker
 * @param {string[]} contexts the keys of the nested context groups; empty for the speaker's own.
 * @param {string} term the term, as the Web Service lists it.
 * @returns {string|null} the stored source text, or null if the term has no entry there.
 */
export function findStoredText(content, speaker, contexts, term) {
    const group = groupAt(content, [speaker, ...contexts]);
    if (!group) return null;
    if (typeof group[term] === 'string') return term;
    const normalized = normalizeText(term);
    return Object.keys(group).find((key) =>
        typeof group[key] === 'string' && normalizeText(key) === normalized) ?? null;
}

/**
 * A copy of one language's translation content with a term's translation set in a speaker's group
 * (or a context group nested in it), stored under the term itself. An entry stored under text that
 * differs from the term only in whitespace is replaced, not kept next to it (#386).
 *
 * @param {Object} content one language's translation content.
 * @param {string} speaker
 * @param {string[]} contexts the keys of the nested context groups; empty for the speaker's own.
 * @param {string} term the term, as the Web Service lists it.
 * @param {string} translation
 * @returns {Object}
 */
export function withTranslation(content, speaker, contexts, term, translation) {
    const result = JSON.parse(JSON.stringify(content ?? {}));
    let group = result;
    for (const key of [speaker, ...contexts]) {
        if (group[key] === null || typeof group[key] !== 'object') group[key] = {};
        group = group[key];
    }
    const normalized = normalizeText(term);
    for (const key of Object.keys(group)) {
        if (key !== term && typeof group[key] === 'string' && normalizeText(key) === normalized) {
            delete group[key];
        }
    }
    group[term] = translation;
    return result;
}

/**
 * The gender-specific translations of a term for a speaker: the context groups (nested in the
 * speaker's group) that hold an entry for it, as lists of their keys, in file order (#386).
 *
 * @param {Object} content one language's translation content.
 * @param {string} speaker
 * @param {string} term the term, as the Web Service lists it.
 * @returns {string[][]}
 */
export function findContextVariants(content, speaker, term) {
    const normalized = normalizeText(term);
    return listEntries(content)
        .filter((entry) => entry.speaker === speaker && entry.contexts.length > 0
            && normalizeText(entry.text) === normalized)
        .map((entry) => entry.contexts);
}
