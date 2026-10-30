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
import { supportedVariableLines, readOnlyNote } from './supported-variable-hints.js';

describe('supportedVariableLines', () => {
    it('lists the description and every hint the service gave', () => {
        expect(supportedVariableLines({
            name: 'example.choice', description: 'A choice', type: 'string',
            values: ['a', 'b', 'c'], readOnly: false,
        })).toEqual([
            'Supported by the External Variable Service',
            'A choice',
            'Type: string',
            'Allowed values: a, b, c',
            'Dialogues may change it',
        ]);
    });

    it('says a read-only variable doesn\'t accept changes from a dialogue', () => {
        expect(supportedVariableLines({ name: 'dayPart', type: 'string', readOnly: true }))
            .toContain('Read-only: the service doesn\'t accept changes from a dialogue');
    });

    it('leaves out the hints the service didn\'t give, never guessing them', () => {
        expect(supportedVariableLines({ name: 'example.noHints', description: null, type: null,
            values: null, readOnly: null }))
            .toEqual(['Supported by the External Variable Service']);
    });

    it('shows values of any type as text', () => {
        expect(supportedVariableLines({ name: 'n', values: [1, true] }))
            .toContain('Allowed values: 1, true');
    });

    it('gives nothing for a variable the service doesn\'t report', () => {
        expect(supportedVariableLines(null)).toEqual([]);
    });
});

describe('readOnlyNote', () => {
    it('says a change to the test values stays there', () => {
        expect(readOnlyNote(true)).toContain('only changes the test values');
    });

    it('says a change to the stored value is replaced at the next dialogue start', () => {
        expect(readOnlyNote(false)).toContain('replaced by the service\'s value at the next dialogue start');
    });
});
