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
import { describe, it, expect, afterEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ContextMenu from './ContextMenu.vue';

// The ContextMenu widget: items, separators, submenus, keyboard navigation, closing, and keeping
// itself inside the window.

const ITEMS = [
    { label: 'Open', icon: 'fa-solid fa-play', test: 'open' },
    { label: 'Rename…', test: 'rename' },
    { label: 'Disabled', disabled: true, test: 'disabled' },
    { separator: true },
    {
        label: 'Insert',
        test: 'insert',
        children: [
            { label: 'Statement', test: 'statement' },
            { label: 'Reply', test: 'reply' },
        ],
    },
    { label: 'Delete…', danger: true, test: 'delete' },
];

const wrappers = [];

async function mountMenu(props = {}) {
    const wrapper = mount(ContextMenu, {
        props: { items: ITEMS, x: 100, y: 100, ...props },
        global: { stubs: { FontAwesomeIcon: true } },
        attachTo: document.body,
    });
    wrappers.push(wrapper);
    await flushPromises();
    return wrapper;
}

const item = (test) => document.querySelector(`[data-test="${test}"]`);
const key = (element, k) => element.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));

afterEach(() => {
    while (wrappers.length) wrappers.pop().unmount();
    document.body.innerHTML = '';
    vi.restoreAllMocks();
});

describe('ContextMenu', () => {
    it('renders items, separators and a destructive item, with menu roles', async () => {
        await mountMenu();
        const menu = document.querySelector('[role="menu"]');
        expect(menu).not.toBeNull();
        expect([...menu.querySelectorAll(':scope > div > [role="menuitem"]')].map((b) => b.textContent.trim()))
            .toEqual(['Open', 'Rename…', 'Disabled', 'Insert', 'Delete…']);
        expect(menu.querySelectorAll('[role="separator"]')).toHaveLength(1);
        expect(item('delete').className).toContain('text-red-dark');
        expect(item('disabled').disabled).toBe(true);
        expect(item('insert').getAttribute('aria-haspopup')).toBe('menu');
    });

    it('focuses the first item, and moves with the arrow keys past disabled items and separators', async () => {
        await mountMenu();
        expect(document.activeElement).toBe(item('open'));
        key(item('open'), 'ArrowDown');
        expect(document.activeElement).toBe(item('rename'));
        key(item('rename'), 'ArrowDown');
        expect(document.activeElement).toBe(item('insert'));
        key(item('insert'), 'ArrowUp');
        expect(document.activeElement).toBe(item('rename'));
        key(item('rename'), 'End');
        expect(document.activeElement).toBe(item('delete'));
        key(item('delete'), 'ArrowDown');
        expect(document.activeElement).toBe(item('open'));
    });

    it('chooses an item with a click or Enter, then closes', async () => {
        const wrapper = await mountMenu();
        item('rename').click();
        expect(wrapper.emitted('select')[0][0].label).toBe('Rename…');
        expect(wrapper.emitted('close')).toHaveLength(1);

        wrapper.unmount();
        wrappers.length = 0;
        const other = await mountMenu();
        key(item('open'), 'Enter');
        expect(other.emitted('select')[0][0].label).toBe('Open');
    });

    it('does not choose a disabled item', async () => {
        const wrapper = await mountMenu();
        item('disabled').click();
        expect(wrapper.emitted('select')).toBeUndefined();
    });

    it('opens a submenu with the keyboard, chooses in it, and goes back with ArrowLeft', async () => {
        const wrapper = await mountMenu();
        item('insert').focus();
        key(item('insert'), 'ArrowRight');
        await flushPromises();
        expect(item('insert').getAttribute('aria-expanded')).toBe('true');
        expect(document.activeElement).toBe(item('statement'));
        key(item('statement'), 'ArrowLeft');
        await flushPromises();
        expect(item('statement')).toBeNull();
        expect(document.activeElement).toBe(item('insert'));

        key(item('insert'), 'Enter');
        await flushPromises();
        key(item('statement'), 'ArrowDown');
        key(item('reply'), 'Enter');
        expect(wrapper.emitted('select')[0][0].label).toBe('Reply');
    });

    it('opens a submenu on hover, without taking the focus', async () => {
        await mountMenu();
        item('insert').dispatchEvent(new Event('pointerenter'));
        await flushPromises();
        expect(item('statement')).not.toBeNull();
        expect(document.activeElement).toBe(item('open'));
        item('open').dispatchEvent(new Event('pointerenter'));
        await flushPromises();
        expect(item('statement')).toBeNull();
    });

    it('closes on Escape and gives the focus back', async () => {
        const trigger = document.createElement('button');
        document.body.appendChild(trigger);
        trigger.focus();
        const wrapper = await mountMenu();
        key(item('open'), 'Escape');
        expect(wrapper.emitted('close')).toHaveLength(1);
        expect(document.activeElement).toBe(trigger);
    });

    it('closes on a click outside it, but not on one inside it', async () => {
        const wrapper = await mountMenu();
        item('insert').dispatchEvent(new Event('pointerdown', { bubbles: true }));
        expect(wrapper.emitted('close')).toBeUndefined();
        document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
        expect(wrapper.emitted('close')).toHaveLength(1);
    });

    it('closes when the window scrolls or resizes', async () => {
        const wrapper = await mountMenu();
        window.dispatchEvent(new Event('resize'));
        expect(wrapper.emitted('close')).toHaveLength(1);
    });

    it('keeps itself inside the window, flipping to the other side of the pointer', async () => {
        vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(
            { left: 0, top: 0, right: 224, bottom: 200, width: 224, height: 200 });
        await mountMenu({ x: window.innerWidth - 10, y: window.innerHeight - 10 });
        const menu = document.querySelector('[role="menu"]');
        expect(menu.style.left).toBe(`${window.innerWidth - 10 - 224}px`);
        expect(menu.style.top).toBe(`${window.innerHeight - 10 - 200}px`);
        expect(menu.style.visibility).toBe('visible');
    });

    it('opens below an anchor element', async () => {
        const anchor = document.createElement('button');
        document.body.appendChild(anchor);
        vi.spyOn(anchor, 'getBoundingClientRect').mockReturnValue(
            { left: 40, top: 60, right: 60, bottom: 80, width: 20, height: 20 });
        await mountMenu({ x: null, y: null, anchor });
        const menu = document.querySelector('[role="menu"]');
        expect(menu.style.left).toBe('40px');
        expect(menu.style.top).toBe('80px');
    });
});
