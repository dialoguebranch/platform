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
import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import ResizablePanels from './ResizablePanels.vue';

// Either side panel collapses to a strip that opens it again, and that is remembered.

function mountPanels() {
    return mount(ResizablePanels, {
        props: { cookiePrefix: 'spec', mobileTabNames: ['A', 'B', 'C'], leftPanelLabel: 'Dialogues', rightPanelLabel: 'Variables' },
        slots: {
            left: '<div data-test="left">left</div>',
            main: '<div data-test="main">main</div>',
            right: '<div data-test="right">right</div>',
        },
        global: { stubs: { FontAwesomeIcon: true } },
    });
}

beforeEach(() => {
    for (const cookie of document.cookie.split(';')) {
        const name = cookie.split('=')[0].trim();
        if (name) document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    }
});

describe('ResizablePanels', () => {
    for (const [side, other] of [['left', 'right'], ['right', 'left']]) {
        it(`collapses the ${side} panel to a strip that opens it again`, async () => {
            const wrapper = mountPanels();
            wrapper.vm.collapsePanel(side);
            await nextTick();

            expect(wrapper.find(`[data-test="${side}"]`).exists()).toBe(false);
            expect(wrapper.find(`[data-test="${other}"]`).exists()).toBe(true);
            const strip = wrapper.get(`[data-test="expand-${side}-panel"]`);
            expect(strip.attributes('title')).toBe(side === 'left' ? 'Show Dialogues' : 'Show Variables');

            await strip.trigger('click');
            expect(wrapper.find(`[data-test="${side}"]`).exists()).toBe(true);
            expect(wrapper.find(`[data-test="expand-${side}-panel"]`).exists()).toBe(false);
        });
    }

    it('remembers which panels are collapsed', async () => {
        mountPanels().vm.collapsePanel('left');
        const wrapper = mountPanels();
        await nextTick();

        expect(wrapper.find('[data-test="expand-left-panel"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="expand-right-panel"]').exists()).toBe(false);
    });
});
