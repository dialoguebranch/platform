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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';

// The node edit panel's `comment` field (#348): read from the node's tags, saved as a tag with
// the rest of the node, and removed when emptied.

const authoring = {
    updateDraftNode: vi.fn(),
    findNodeReferences: vi.fn(),
    renameDraftNode: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: NodeEditPanel } = await import('./NodeEditPanel.vue');

const NODE = {
    title: 'Start',
    tags: [
        { key: 'title', value: 'Start' },
        { key: 'speaker', value: 'Robin' },
        { key: 'comment', value: 'Greets the user' },
        { key: 'position', value: '10,20' },
    ],
    body: 'Hello.',
};

function mountPanel(node = NODE) {
    return mount(NodeEditPanel, {
        props: { node, dialogueName: 'main' },
        global: {
            provide: { state: ref({ selectedProject: { slug: 'default-test' } }) },
            stubs: { FontAwesomeIcon: true, teleport: true },
        },
    });
}

async function save(wrapper) {
    await wrapper.findAll('button').find((b) => b.text() === 'Save').trigger('click');
    await flushPromises();
}

const savedTags = () => authoring.updateDraftNode.mock.calls.at(-1)[3];

describe('NodeEditPanel speaker', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.updateDraftNode.mockImplementation(async (slug, dialogue, title, tags, body) =>
            ({ title, tags, body }));
    });

    it('refuses the reserved speaker name _user without saving', async () => {
        const wrapper = mountPanel();
        await wrapper.find('[data-test="node-speaker"]').setValue(' _user ');
        await save(wrapper);
        expect(authoring.updateDraftNode).not.toHaveBeenCalled();
        expect(wrapper.text()).toContain('Speaker name "_user" is reserved');
    });

    it('saves a similar speaker name', async () => {
        const wrapper = mountPanel();
        await wrapper.find('[data-test="node-speaker"]').setValue('_User');
        await save(wrapper);
        expect(savedTags().find((tag) => tag.key === 'speaker').value).toBe('_User');
    });
});

describe('NodeEditPanel comment', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.updateDraftNode.mockImplementation(async (slug, dialogue, title, tags, body) =>
            ({ title, tags, body }));
    });

    it('shows the node\'s comment', () => {
        const wrapper = mountPanel();
        expect(wrapper.find('[data-test="node-comment"]').element.value).toBe('Greets the user');
    });

    it('saves a changed comment as a tag, keeping the other tags in place', async () => {
        const wrapper = mountPanel();
        await wrapper.find('[data-test="node-comment"]').setValue('  Asks how the user slept  ');
        await save(wrapper);
        const tags = savedTags();
        expect(tags.map((tag) => tag.key)).toEqual(['title', 'speaker', 'comment', 'position', 'colorId']);
        expect(tags.find((tag) => tag.key === 'comment').value).toBe('Asks how the user slept');
    });

    it('removes the tag when the comment is emptied', async () => {
        const wrapper = mountPanel();
        await wrapper.find('[data-test="node-comment"]').setValue('   ');
        await save(wrapper);
        expect(savedTags().some((tag) => tag.key === 'comment')).toBe(false);
    });

    it('adds no comment tag to a node without one', async () => {
        const wrapper = mountPanel({ ...NODE, tags: NODE.tags.filter((tag) => tag.key !== 'comment') });
        expect(wrapper.find('[data-test="node-comment"]').element.value).toBe('');
        await save(wrapper);
        expect(savedTags().some((tag) => tag.key === 'comment')).toBe(false);
    });
});

// The body's "insert statement" menu, a ContextMenu (#389).
describe('NodeEditPanel insert menu', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('opens on right-click in the body and inserts the chosen snippet at the cursor', async () => {
        const wrapper = mountPanel();
        const textarea = wrapper.find('textarea');
        textarea.element.setSelectionRange(0, 0);
        await textarea.trigger('contextmenu', { clientX: 50, clientY: 60 });
        await flushPromises();
        const menu = wrapper.find('[data-test="context-menu"]');
        expect(menu.exists()).toBe(true);
        await menu.findAll('[role="menuitem"]').find((b) => b.text() === 'Random options').trigger('click');
        await flushPromises();
        const body = wrapper.find('textarea').element.value;
        expect(body.startsWith('<<random>>')).toBe(true);
        expect(body).toContain('Hello.');
        expect(wrapper.find('[data-test="context-menu"]').exists()).toBe(false);
    });

    it('offers submenus, such as the kinds of user input', async () => {
        const wrapper = mountPanel();
        await wrapper.find('textarea').trigger('contextmenu', { clientX: 50, clientY: 60 });
        await flushPromises();
        const userInput = wrapper.findAll('[role="menuitem"]').find((b) => b.text() === 'User input');
        expect(userInput.attributes('aria-haspopup')).toBe('menu');
        await userInput.trigger('click');
        await flushPromises();
        expect(wrapper.findAll('[role="menuitem"]').map((b) => b.text())).toContain('Multiple choice');
    });
});

const { setProjectSpeakers } = await import('@/composables/project-speakers.js');

describe('NodeEditPanel project speakers (#396)', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.updateDraftNode.mockImplementation(async (slug, dialogue, title, tags, body) =>
            ({ title, tags, body }));
        setProjectSpeakers([
            { name: 'Robin', gender: 'female', default: true },
            { name: 'Martin McOwl', gender: 'male', default: false },
        ]);
    });

    afterEach(() => setProjectSpeakers([]));

    const options = (wrapper) => wrapper.findAll('[data-test="node-speaker-select"] option')
        .map((o) => o.text());

    it('chooses the speaker from the project\'s speakers', async () => {
        const wrapper = mountPanel();
        expect(wrapper.find('[data-test="node-speaker"]').exists()).toBe(false);
        expect(options(wrapper)).toEqual(['Robin', 'Martin McOwl']);

        await wrapper.find('[data-test="node-speaker-select"]').setValue('Martin McOwl');
        await save(wrapper);
        expect(savedTags().find((tag) => tag.key === 'speaker').value).toBe('Martin McOwl');
    });

    it('keeps a speaker that isn\'t one of the project\'s, marked as such', () => {
        const wrapper = mountPanel({ ...NODE, tags: NODE.tags.map((t) =>
            t.key === 'speaker' ? { key: 'speaker', value: 'Martin McOlw' } : t) });
        expect(options(wrapper)).toContain('Martin McOlw (not a project speaker)');
        expect(wrapper.find('[data-test="node-speaker-select"]').element.value).toBe('Martin McOlw');
        expect(wrapper.find('[data-test="node-undefined-speaker-warning"]').exists()).toBe(true);
    });

    it('shows no warning for one of the project\'s speakers', () => {
        const wrapper = mountPanel();
        expect(wrapper.find('[data-test="node-undefined-speaker-warning"]').exists()).toBe(false);
    });

    it('stays free text when the project has no speakers', () => {
        setProjectSpeakers([]);
        const wrapper = mountPanel();
        expect(wrapper.find('[data-test="node-speaker"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="node-speaker-select"]').exists()).toBe(false);
    });

    it('doesn\'t save a node without a speaker', async () => {
        const wrapper = mountPanel({ ...NODE, tags: NODE.tags.filter((t) => t.key !== 'speaker') });
        await save(wrapper);
        expect(authoring.updateDraftNode).not.toHaveBeenCalled();
        expect(wrapper.text()).toContain('Choose a speaker');
    });

    it('saves the End node without a speaker', async () => {
        const wrapper = mountPanel({ title: 'End', tags: [{ key: 'title', value: 'End' }], body: '' });
        await save(wrapper);
        expect(authoring.updateDraftNode).toHaveBeenCalled();
    });
});
