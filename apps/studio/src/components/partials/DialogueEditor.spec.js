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
import { defineComponent, h, ref } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';

// Moving several selected nodes together saves every one's position, not only the one that was
// grabbed (#196). Vue Flow itself is stubbed: the test fires its drag-stop event directly.

const authoring = {
    listDraftNodes: vi.fn(),
    updateDraftNode: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const VueFlowStub = defineComponent({
    name: 'VueFlow',
    emits: ['node-drag-stop'],
    setup(_, { slots }) {
        return () => h('div', slots.default?.());
    },
});

vi.mock('@vue-flow/core', () => ({
    VueFlow: VueFlowStub,
    Panel: defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) }),
    BaseEdge: defineComponent({ setup: () => () => null }),
    MarkerType: { ArrowClosed: 'arrowclosed' },
    getStraightPath: () => ['', 0, 0],
    useVueFlow: () => ({
        fitView: vi.fn(), setCenter: vi.fn(), getViewport: () => ({ x: 0, y: 0, zoom: 1 }),
        screenToFlowCoordinate: (p) => p,
    }),
}));

const { default: DialogueEditor } = await import('./DialogueEditor.vue');

const node = (title, x) => ({
    title, body: 'Hi.',
    tags: [{ key: 'title', value: title }, { key: 'speaker', value: 'Robin' },
        { key: 'position', value: `${x},0` }],
});

async function mountEditor() {
    const wrapper = mount(DialogueEditor, {
        props: { dialogueName: 'main' },
        global: {
            provide: { state: ref({ selectedProject: { slug: 'p' } }) },
            stubs: { FontAwesomeIcon: true, PreambleBar: true, NodeEditPanel: true, teleport: true },
        },
    });
    await flushPromises();
    return wrapper;
}

const dragStop = (wrapper, event) =>
    wrapper.findComponent(VueFlowStub).vm.$emit('node-drag-stop', event);

const savedPositions = () => authoring.updateDraftNode.mock.calls.map((call) =>
    [call[2], call[3].find((tag) => tag.key === 'position').value]);

beforeEach(() => {
    vi.clearAllMocks();
    authoring.listDraftNodes.mockResolvedValue([node('Start', 0), node('Next', 200), node('End', 400)]);
    authoring.updateDraftNode.mockImplementation(async (slug, dialogue, title, tags, body) =>
        ({ title, tags, body }));
});

describe('DialogueEditor dragging (#196)', () => {
    it('saves the position of every node moved together', async () => {
        const wrapper = await mountEditor();
        const moved = [
            { id: 'Start', position: { x: 10, y: 50 } },
            { id: 'Next', position: { x: 210, y: 50 } },
            { id: 'End', position: { x: 410, y: 50 } },
        ];
        dragStop(wrapper, { node: moved[0], nodes: moved });
        await flushPromises();

        expect(savedPositions()).toEqual([['Start', '10,50'], ['Next', '210,50'], ['End', '410,50']]);
        expect(wrapper.emitted('dialogueSaved')).toHaveLength(1);
    });

    it('saves one node dragged on its own', async () => {
        const wrapper = await mountEditor();
        const moved = { id: 'Next', position: { x: 300, y: 20 } };
        dragStop(wrapper, { node: moved, nodes: [moved] });
        await flushPromises();
        expect(savedPositions()).toEqual([['Next', '300,20']]);
    });

    it('stops at a failure and shows it, keeping what was saved', async () => {
        authoring.updateDraftNode
            .mockImplementationOnce(async (slug, dialogue, title, tags, body) => ({ title, tags, body }))
            .mockRejectedValueOnce(new Error('Server down'));
        const wrapper = await mountEditor();
        const moved = [
            { id: 'Start', position: { x: 1, y: 1 } },
            { id: 'Next', position: { x: 2, y: 2 } },
            { id: 'End', position: { x: 3, y: 3 } },
        ];
        dragStop(wrapper, { node: moved[0], nodes: moved });
        await flushPromises();
        expect(authoring.updateDraftNode).toHaveBeenCalledTimes(2);
        expect(wrapper.emitted('dialogueSaved')).toHaveLength(1);
    });
});
