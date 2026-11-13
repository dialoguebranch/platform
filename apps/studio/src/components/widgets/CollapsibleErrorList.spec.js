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
import { mount } from '@vue/test-utils';
import CollapsibleErrorList from './CollapsibleErrorList.vue';

// The parse errors of one or more dialogues, folded away behind a short summary.

const summary = (errors) => mount(CollapsibleErrorList, { props: { errors } }).find('summary').text();

describe('CollapsibleErrorList', () => {
    it('counts the errors of one dialogue', () => {
        expect(summary({ poe: ['a', 'b', 'c'] })).toBe('Show details (3 errors)');
        expect(summary({ poe: ['a'] })).toBe('Show details (1 error)');
    });

    it('counts the errors and the dialogues when there are several', () => {
        expect(summary({ poe: ['a', 'b'], basic: ['c'] })).toBe('Show details (3 errors in 2 dialogues)');
    });

    it('lists every error, per dialogue, inside the fold', () => {
        const wrapper = mount(CollapsibleErrorList, { props: { errors: { poe: ['first', 'second'] } } });
        expect(wrapper.findAll('li').map((li) => li.text())).toEqual(['first', 'second']);
        expect(wrapper.find('details').attributes('open')).toBeUndefined();
    });

    it('shows nothing without errors', () => {
        expect(mount(CollapsibleErrorList, { props: { errors: null } }).find('details').exists()).toBe(false);
    });
});
