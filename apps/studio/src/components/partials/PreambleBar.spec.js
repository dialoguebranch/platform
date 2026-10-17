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

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';

// The graph editor's preamble bar (#346): shows the dialogue's preamble as plain text, offers to
// add one when there is none, and saves edits as `//` comment lines.

const authoring = {
    listDraftDialogues: vi.fn(),
    updateDraftPreamble: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: PreambleBar } = await import('./PreambleBar.vue');

function dialogues(preamble) {
    return [
        { name: 'default', preamble: '// Not this one.' },
        { name: 'main', preamble },
    ];
}

async function mountBar() {
    const wrapper = mount(PreambleBar, {
        props: { dialogueName: 'main' },
        global: {
            provide: { state: ref({ selectedProject: { slug: 'default-test' } }) },
            stubs: { FontAwesomeIcon: true },
        },
    });
    await flushPromises();
    return wrapper;
}

describe('PreambleBar', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('offers to add a script comment when the dialogue has none', async () => {
        authoring.listDraftDialogues.mockResolvedValue(dialogues(''));
        const wrapper = await mountBar();
        expect(wrapper.find('[data-test="preamble-add"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="preamble-shown"]').exists()).toBe(false);
    });

    it('shows this dialogue\'s preamble as plain text, and collapses to its first line', async () => {
        authoring.listDraftDialogues.mockResolvedValue(dialogues('// First line.\n//   Indented.'));
        const wrapper = await mountBar();
        expect(wrapper.find('[data-test="preamble-shown"]').text()).toContain('First line.');
        expect(wrapper.find('[data-test="preamble-shown"]').element.textContent).toBe('First line.\n  Indented.');
        await wrapper.find('button[title="Hide script comment"]').trigger('click');
        const collapsedButton = wrapper.find('[data-test="preamble-collapsed"]');
        expect(collapsedButton.element.tagName).toBe('BUTTON');
        expect(collapsedButton.text()).toBe('');
        expect(collapsedButton.attributes('title')).toBe('First line.\n  Indented.');
        expect(wrapper.find('[data-test="preamble-edit"]').exists()).toBe(false);

        await collapsedButton.trigger('click');
        expect(wrapper.find('[data-test="preamble-shown"]').element.textContent).toBe('First line.\n  Indented.');
        expect(wrapper.find('[data-test="preamble-edit"]').exists()).toBe(true);
    });

    it('saves edited text as comment lines and reports the save', async () => {
        authoring.listDraftDialogues.mockResolvedValue(dialogues(''));
        authoring.updateDraftPreamble.mockResolvedValue({ preamble: '// What this is for.\n//\n// Second.' });
        const wrapper = await mountBar();
        await wrapper.find('[data-test="preamble-add"]').trigger('click');
        await wrapper.find('[data-test="preamble-text"]').setValue('What this is for.\n\nSecond.');
        await wrapper.find('[data-test="preamble-save"]').trigger('click');
        await flushPromises();
        expect(authoring.updateDraftPreamble).toHaveBeenCalledWith('default-test', 'main',
            '// What this is for.\n//\n// Second.');
        expect(wrapper.emitted('saved')).toHaveLength(1);
        expect(wrapper.find('[data-test="preamble-shown"]').element.textContent).toBe('What this is for.\n\nSecond.');
    });

    it('keeps the editor open and shows the error when saving fails', async () => {
        authoring.listDraftDialogues.mockResolvedValue(dialogues('// Old.'));
        authoring.updateDraftPreamble.mockRejectedValue(
            { status: 400, message: "Line 2 of the preamble isn't a comment line" });
        const wrapper = await mountBar();
        await wrapper.find('[data-test="preamble-edit"]').trigger('click');
        expect(wrapper.find('[data-test="preamble-text"]').element.value).toBe('Old.');
        await wrapper.find('[data-test="preamble-save"]').trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="preamble-text"]').exists()).toBe(true);
        expect(wrapper.text()).toContain("Line 2 of the preamble isn't a comment line");
        expect(wrapper.emitted('saved')).toBeUndefined();
    });
});
