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

// A project's speakers in Studio (#395, #396): the draft speakers of the open project, shared by
// Configure Project, the node edit panel and the graph editor, plus the rules for editing them.
// The web service checks the same rules (core's Speaker.validate); checking them here too only
// lets Studio say what's wrong before saving.

import { ref } from 'vue';
import { USER_SPEAKER } from '@/authoring/DlbHeaderTags.js';

/** The genders a speaker can have, in the order Studio offers them. */
export const SPEAKER_GENDERS = [
    { value: 'undefined', label: 'Undefined' },
    { value: 'female', label: 'Female' },
    { value: 'male', label: 'Male' },
];

/**
 * The draft speakers of the open project, `[{ name, gender, default }]` in their order: set when
 * the project is loaded and after Configure Project saves.
 */
export const projectSpeakers = ref([]);

/**
 * Sets the open project's draft speakers.
 *
 * @param {?Array} speakers the speakers, as `get-project` returns them in `draftSpeakers`.
 */
export function setProjectSpeakers(speakers) {
    projectSpeakers.value = speakers ?? [];
}

/**
 * Returns the speaker a new node gets: the default speaker, or else the first one, or `null` when
 * the project has no speakers.
 *
 * @param {Array} speakers the project's speakers.
 * @returns {?string} the speaker's name, or `null`.
 */
export function speakerForNewNode(speakers) {
    return (speakers.find((s) => s.default) ?? speakers[0])?.name ?? null;
}

/**
 * Checks the name of a speaker about to be added.
 *
 * @param {string} name the name as typed.
 * @param {Array} speakers the speakers already in the list.
 * @returns {?string} what's wrong with it, or `null` if it can be added.
 */
export function checkNewSpeakerName(name, speakers) {
    if (!name || name.trim() === '') return 'A speaker needs a name.';
    if (name !== name.trim()) return 'A speaker\'s name can\'t start or end with spaces.';
    if (name === USER_SPEAKER) return `"${USER_SPEAKER}" is reserved for the user's replies.`;
    if (speakers.some((s) => s.name === name)) return `"${name}" is already a speaker.`;
    return null;
}

/**
 * Returns whether two lists of speakers differ: in names, order, genders or which is the default.
 *
 * @param {?Array} a one list.
 * @param {?Array} b the other list.
 * @returns {boolean} `true` if they differ.
 */
export function speakersDiffer(a, b) {
    const left = a ?? [];
    const right = b ?? [];
    if (left.length !== right.length) return true;
    return left.some((s, i) => s.name !== right[i].name
        || (s.gender ?? 'undefined') !== (right[i].gender ?? 'undefined')
        || !!s.default !== !!right[i].default);
}

/**
 * Returns whether a node's speaker isn't one of the project's speakers, which the publish check
 * reports as a warning (#397). Never for a project without speakers, or a node without a speaker.
 *
 * @param {?string} name the node's speaker.
 * @param {Array} speakers the project's speakers.
 * @returns {boolean} `true` if the speaker is set but undefined in the project.
 */
export function isUndefinedSpeaker(name, speakers) {
    return !!name && speakers.length > 0 && !speakers.some((s) => s.name === name);
}
