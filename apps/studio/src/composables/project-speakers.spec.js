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

import { describe, it, expect } from 'vitest';
import { speakerForNewNode, checkNewSpeakerName, speakersDiffer } from './project-speakers.js';

const SPEAKERS = [
    { name: 'Anna', gender: 'female', default: false },
    { name: 'Martin McOwl', gender: 'male', default: true },
];

describe('speakerForNewNode', () => {
    it('gives the default speaker, else the first, else none', () => {
        expect(speakerForNewNode(SPEAKERS)).toBe('Martin McOwl');
        expect(speakerForNewNode([{ name: 'Anna' }, { name: 'Bob' }])).toBe('Anna');
        expect(speakerForNewNode([])).toBeNull();
    });
});

describe('checkNewSpeakerName', () => {
    it('accepts a new name', () => {
        expect(checkNewSpeakerName('Narrator', SPEAKERS)).toBeNull();
    });

    it('refuses an empty, padded, reserved or existing name', () => {
        expect(checkNewSpeakerName('', SPEAKERS)).toContain('needs a name');
        expect(checkNewSpeakerName('   ', SPEAKERS)).toContain('needs a name');
        expect(checkNewSpeakerName(' Bob', SPEAKERS)).toContain('spaces');
        expect(checkNewSpeakerName('_user', SPEAKERS)).toContain('reserved');
        expect(checkNewSpeakerName('Anna', SPEAKERS)).toContain('already');
    });
});

describe('speakersDiffer', () => {
    it('sees no difference between equal lists, counting a missing gender as undefined', () => {
        expect(speakersDiffer([{ name: 'A', gender: 'undefined' }], [{ name: 'A' }])).toBe(false);
        expect(speakersDiffer(null, [])).toBe(false);
    });

    it('sees a different name, order, gender or default', () => {
        expect(speakersDiffer(SPEAKERS, [...SPEAKERS].reverse())).toBe(true);
        expect(speakersDiffer(SPEAKERS, SPEAKERS.slice(1))).toBe(true);
        expect(speakersDiffer([{ name: 'A', gender: 'male' }], [{ name: 'A', gender: 'female' }])).toBe(true);
        expect(speakersDiffer([{ name: 'A', default: true }], [{ name: 'A' }])).toBe(true);
    });
});
