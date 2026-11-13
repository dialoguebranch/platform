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
import { defineComponent, h, ref } from 'vue';
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';

// Moving several selected nodes together saves every one's position, not only the one that was
// grabbed (#196). Vue Flow itself is stubbed: the test fires its drag-stop event directly.

const authoring = {
    listDraftNodes: vi.fn(),
    updateDraftNode: vi.fn(),
    createDraftNode: vi.fn(),
    findNodeReferences: vi.fn(),
    deleteDraftNode: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

// The nodes in `selectedIds` are drawn as selected, as Vue Flow does after a box selection.
const selectedIds = new Set();

const VueFlowStub = defineComponent({
    name: 'VueFlow',
    props: ['nodes', 'deleteKeyCode', 'zoomOnDoubleClick'],
    emits: ['node-drag-stop', 'node-click', 'pane-context-menu', 'node-context-menu'],
    setup(props, { slots }) {
        return () => h('div', [
            ...(props.nodes ?? []).map((node) => h('div', { 'data-node': node.id },
                slots['node-dialogueNode']?.({ id: node.id, data: node.data, selected: selectedIds.has(node.id) || !!node.selected }))),
            slots.default?.(),
        ]);
    },
});

// Moving the view, shared so the tests can see where it went.
const flowView = { setCenter: vi.fn() };

vi.mock('@vue-flow/core', () => ({
    VueFlow: VueFlowStub,
    Panel: defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) }),
    BaseEdge: defineComponent({ setup: () => () => null }),
    MarkerType: { ArrowClosed: 'arrowclosed' },
    getStraightPath: () => ['', 0, 0],
    useVueFlow: () => ({
        fitView: vi.fn(), setCenter: flowView.setCenter, getViewport: () => ({ x: 0, y: 0, zoom: 1 }),
        screenToFlowCoordinate: (p) => p,
        // Every node measures 100 x 40 at its stored position, for the MiniMap's lines.
        findNode: (id) => {
            const stored = currentNodes.find((n) => n.title === id);
            if (!stored) return undefined;
            const [x, y] = stored.tags.find((t) => t.key === 'position').value.split(',').map(Number);
            return { computedPosition: { x, y }, dimensions: { width: 100, height: 40 } };
        },
    }),
}));

// The canvas chrome (#192): stand-ins that keep their props, so the tests can check them.
const chrome = (name) => defineComponent({
    name, inheritAttrs: false,
    props: ['variant', 'position', 'nodeColor', 'pannable', 'zoomable', 'fitViewParams'],
    setup: () => () => h('div'),
});
const BackgroundStub = chrome('Background');
const ControlsStub = defineComponent({
    name: 'Controls', inheritAttrs: false,
    props: ['position', 'fitViewParams'],
    setup: (_, { slots }) => () => h('div', slots.default?.()),
});
const ControlButtonStub = defineComponent({
    name: 'ControlButton',
    setup: (_, { slots }) => () => h('button', slots.default?.()),
});
// The MiniMap stand-in draws its first node through the editor's slot, as the real one does for
// every node.
const MiniMapStub = defineComponent({
    name: 'MiniMap', inheritAttrs: false,
    props: ['position', 'nodeColor', 'pannable', 'zoomable'],
    setup: (_, { slots }) => () => h('svg', slots['node-dialogueNode']?.({
        id: 'Start', position: { x: 0, y: 0 }, dimensions: { width: 100, height: 40 },
        color: '#000', borderRadius: 4,
    })),
});
vi.mock('@vue-flow/background', () => ({ Background: BackgroundStub }));
vi.mock('@vue-flow/controls', () => ({ Controls: ControlsStub, ControlButton: ControlButtonStub }));
vi.mock('@vue-flow/minimap', () => ({ MiniMap: MiniMapStub }));

const { default: DialogueEditor } = await import('./DialogueEditor.vue');

// The editor listens for keys on the document, so no editor may outlive its test.
enableAutoUnmount(afterEach);
const { DEFAULT_NODE_COLOR } = await import('@/composables/node-colors.js');

let currentNodes = [];

const node = (title, x, body = 'Hi.') => ({
    title, body,
    tags: [{ key: 'title', value: title }, { key: 'speaker', value: 'Robin' },
        { key: 'position', value: `${x},0` }],
});

async function mountEditor(options = {}) {
    const wrapper = mount(DialogueEditor, {
        ...options,
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
    selectedIds.clear();
    currentNodes = [node('Start', 0), node('Next', 200), node('End', 400)];
    authoring.listDraftNodes.mockImplementation(async () => currentNodes);
    authoring.createDraftNode.mockResolvedValue({});
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

describe('DialogueEditor canvas chrome (#192)', () => {
    it('shows a dot grid, the zoom controls and a map of the dialogue', async () => {
        const wrapper = await mountEditor();
        expect(wrapper.findComponent(BackgroundStub).props('variant')).toBe('dots');
        expect(wrapper.findComponent(ControlsStub).props('position')).toBe('bottom-left');
        const map = wrapper.findComponent(MiniMapStub);
        expect(map.props('position')).toBe('bottom-right');
        expect(map.props('pannable')).not.toBe(false);
        expect(map.props('zoomable')).not.toBe(false);
    });

    it('colours the map\'s nodes as the graph does, with plain colours', async () => {
        document.documentElement.style.setProperty('--color-node-start', '#04aa6d');
        document.documentElement.style.setProperty('--color-node-end', '#44403c');
        const color = (await mountEditor()).findComponent(MiniMapStub).props('nodeColor');

        expect(color({ data: { isStart: true } })).toBe('#04aa6d');
        expect(color({ data: { isEnd: true } })).toBe('#44403c');
        expect(color({ data: { nodeColor: '#123abc' } })).toBe('#123abc');
        expect(color({ data: {} })).toBe(DEFAULT_NODE_COLOR);
    });

    it('draws the connector lines on the map, between the centres of the nodes', async () => {
        currentNodes = [node('Start', 0, '[[Go|Next]]'), node('Next', 200, '[[On|End]]'),
            node('End', 400, '')];
        const wrapper = await mountEditor();
        const lines = wrapper.findAll('[data-test="minimap-edges"] line').map((line) =>
            ['x1', 'y1', 'x2', 'y2'].map((a) => Number(line.attributes(a))));

        expect(lines).toEqual([[50, 20, 250, 20], [250, 20, 450, 20]]);
    });
});

describe('DialogueEditor selecting several nodes', () => {
    const click = (wrapper, id, keys = {}) =>
        wrapper.findComponent(VueFlowStub).vm.$emit('node-click', { event: keys, node: { id } });

    it('shows which nodes are selected', async () => {
        selectedIds.add('Next');
        const wrapper = await mountEditor();
        const marked = wrapper.findAll('[data-selected="true"]');
        expect(marked).toHaveLength(1);
        expect(wrapper.find('[data-node="Next"] [data-selected="true"]').exists()).toBe(true);
    });

    it('opens a node on a plain click, but not on a click that changes the selection', async () => {
        for (const keys of [{ shiftKey: true }, { ctrlKey: true }, { metaKey: true }]) {
            const wrapper = await mountEditor();
            click(wrapper, 'Next', keys);
            await flushPromises();
            expect(wrapper.findComponent({ name: 'NodeEditPanel' }).exists()).toBe(false);
        }
        const wrapper = await mountEditor();
        click(wrapper, 'Next');
        await flushPromises();
        expect(wrapper.findComponent({ name: 'NodeEditPanel' }).exists()).toBe(true);
    });
});

describe('DialogueEditor mouse and keyboard hints', () => {
    it('opens the hints from the ? button, and closes them again', async () => {
        const wrapper = await mountEditor();
        expect(wrapper.find('[data-test="graph-hints"]').exists()).toBe(false);

        await wrapper.find('[data-test="graph-hints-button"]').trigger('click');
        const hints = wrapper.find('[data-test="graph-hints"]');
        expect(hints.exists()).toBe(true);
        expect(hints.text()).toContain('Shift');
        expect(hints.text()).toContain('Move all selected nodes together');

        await wrapper.find('[data-test="graph-hints-close"]').trigger('click');
        expect(wrapper.find('[data-test="graph-hints"]').exists()).toBe(false);

        await wrapper.find('[data-test="graph-hints-button"]').trigger('click');
        await wrapper.find('[data-test="graph-hints-button"]').trigger('click');
        expect(wrapper.find('[data-test="graph-hints"]').exists()).toBe(false);
    });
});

describe('DialogueEditor keys', () => {
    // Vue Flow deletes the selected nodes on Backspace by default, but only on screen: they'd come
    // back on the next load. The editor handles the delete keys itself, with a confirmation.
    it('turns off Vue Flow\'s delete key', async () => {
        const wrapper = await mountEditor();
        expect(wrapper.findComponent(VueFlowStub).props('deleteKeyCode')).toBe(false);
    });
});

describe('DialogueEditor adding a node from the canvas', () => {
    const rightClick = (wrapper, keys = {}) =>
        wrapper.findComponent(VueFlowStub).vm.$emit('pane-context-menu',
            { preventDefault: () => {}, clientX: 300, clientY: 120, ...keys });
    const createdAt = () => authoring.createDraftNode.mock.calls.map((call) =>
        call[2].find((tag) => tag.key === 'position').value);

    it('opens a menu on a right-click, and adds nothing until it is chosen', async () => {
        const wrapper = await mountEditor();
        rightClick(wrapper);
        await flushPromises();

        expect(wrapper.find('[data-test="pane-menu-add-node"]').exists()).toBe(true);
        expect(authoring.createDraftNode).not.toHaveBeenCalled();

        await wrapper.find('[data-test="pane-menu-add-node"]').trigger('click');
        await flushPromises();
        // centred on the point that was right-clicked (a new node is 180 x 30)
        expect(createdAt()).toEqual(['210,105']);
        expect(wrapper.find('[data-test="pane-menu-add-node"]').exists()).toBe(false);
    });

    it('adds the node at once on a double-click on the empty canvas, without a menu', async () => {
        const wrapper = await mountEditor();
        const pane = document.createElement('div');
        pane.className = 'vue-flow__pane';
        wrapper.element.appendChild(pane);
        pane.dispatchEvent(new MouseEvent('dblclick', { bubbles: true, clientX: 300, clientY: 120 }));
        await flushPromises();

        expect(wrapper.find('[data-test="pane-menu-add-node"]').exists()).toBe(false);
        expect(createdAt()).toEqual(['210,105']);
    });

    it('gives a new node the dialogue\'s speaker when the project has none', async () => {
        // the test nodes all have the speaker Robin, and the project's speakers aren't loaded
        const wrapper = await mountEditor();
        rightClick(wrapper);
        await flushPromises();
        await wrapper.find('[data-test="pane-menu-add-node"]').trigger('click');
        await flushPromises();

        const tags = authoring.createDraftNode.mock.calls[0][2];
        expect(tags.find((tag) => tag.key === 'speaker')?.value).toBe('Robin');
    });

    it('adds nothing on a double-click on a node', async () => {
        const wrapper = await mountEditor();
        await wrapper.find('[data-node="Next"]').trigger('dblclick');
        await flushPromises();
        expect(authoring.createDraftNode).not.toHaveBeenCalled();
    });

    it('turns off Vue Flow\'s zoom on a double-click', async () => {
        const wrapper = await mountEditor();
        expect(wrapper.findComponent(VueFlowStub).props('zoomOnDoubleClick')).toBe(false);
    });
});

describe('DialogueEditor node cards (#200)', () => {
    it('shows a preview of the node\'s text, without its replies and commands', async () => {
        currentNodes = [node('Start', 0, '<<set $a = 1>>Hello there, $name.\n[[Go|Next]]'),
            node('Next', 200, '[[On|End]]'), node('End', 400, '')];
        const wrapper = await mountEditor();

        expect(wrapper.find('[data-node="Start"] [data-test="node-preview"]').text())
            .toBe('Hello there, $name.');
        expect(wrapper.find('[data-node="Next"] [data-test="node-preview"]').exists()).toBe(false);
    });
});

describe('DialogueEditor deleting the selected nodes (#193, #197)', () => {
    // Selects nodes the way Vue Flow does: through the nodes' v-model.
    async function select(wrapper, ...titles) {
        const flow = wrapper.findComponent(VueFlowStub);
        flow.vm.$emit('update:nodes', flow.props('nodes').map((n) => ({ ...n, selected: titles.includes(n.id) })));
        await flushPromises();
    }
    const press = async (key, target = document.body) => {
        target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
        await flushPromises();
    };
    const dialog = (wrapper) => wrapper.find('[data-test="delete-nodes-dialog"]');

    beforeEach(() => {
        authoring.deleteDraftNode.mockResolvedValue({});
        // Start links to Next, Next to End; another dialogue links to Next too.
        authoring.findNodeReferences.mockImplementation(async (slug, dialogue, title) => ({
            Next: [{ dialogueName: 'main', nodeTitle: 'Start' }, { dialogueName: 'other', nodeTitle: 'Hub' }],
            End: [{ dialogueName: 'main', nodeTitle: 'Next' }],
        })[title] ?? []);
    });

    it('asks before deleting, and counts only the links from outside what is deleted', async () => {
        const wrapper = await mountEditor();
        await select(wrapper, 'Next', 'End');
        await press('Delete');

        expect(dialog(wrapper).exists()).toBe(true);
        expect(wrapper.find('[data-test="delete-nodes-titles"]').text()).toBe('Next, End');
        // Next <- Start and Next <- other.Hub; End <- Next doesn't count, Next goes too
        expect(wrapper.find('[data-test="delete-nodes-references"]').text()).toContain('2 replies in 2 dialogues');
        expect(authoring.deleteDraftNode).not.toHaveBeenCalled();

        const loads = authoring.listDraftNodes.mock.calls.length;
        await wrapper.find('[data-test="delete-nodes-confirm"]').trigger('click');
        await flushPromises();

        expect(authoring.deleteDraftNode.mock.calls.map((c) => c[2])).toEqual(['Next', 'End']);
        expect(wrapper.emitted('nodeDeleted')).toEqual([['Next'], ['End']]);
        expect(wrapper.emitted('dialogueSaved')).toHaveLength(1);
        expect(authoring.listDraftNodes.mock.calls.length).toBe(loads + 1);
        expect(dialog(wrapper).exists()).toBe(false);
    });

    it('deletes nothing when cancelled', async () => {
        const wrapper = await mountEditor();
        await select(wrapper, 'Next');
        await press('Backspace');
        await wrapper.find('[data-test="delete-nodes-cancel"]').trigger('click');
        expect(dialog(wrapper).exists()).toBe(false);
        expect(authoring.deleteDraftNode).not.toHaveBeenCalled();
    });

    it('warns when the Start node is among them', async () => {
        const wrapper = await mountEditor();
        await select(wrapper, 'Start');
        await press('Delete');
        expect(wrapper.find('[data-test="delete-nodes-start"]').exists()).toBe(true);
    });

    it('ignores the keys with nothing selected, and while typing', async () => {
        const wrapper = await mountEditor();
        await press('Delete');
        expect(dialog(wrapper).exists()).toBe(false);

        await select(wrapper, 'Next');
        const input = document.createElement('input');
        document.body.appendChild(input);
        await press('Backspace', input);
        input.remove();
        expect(dialog(wrapper).exists()).toBe(false);
        expect(authoring.findNodeReferences).not.toHaveBeenCalled();
    });

    const rightClickNode = async (wrapper, id) => {
        wrapper.findComponent(VueFlowStub).vm.$emit('node-context-menu',
            { event: { preventDefault: () => {}, clientX: 10, clientY: 10 }, node: { id } });
        await flushPromises();
    };

    it('deletes a node from its right-click menu', async () => {
        const wrapper = await mountEditor();
        await rightClickNode(wrapper, 'End');
        const item = wrapper.find('[data-test="node-menu-delete"]');
        expect(item.text()).toContain('Delete node');
        await item.trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="delete-nodes-titles"]').text()).toBe('End');
    });

    it('deletes the whole selection from the menu of a node in it, else only that node', async () => {
        const wrapper = await mountEditor();
        await select(wrapper, 'Next', 'End');
        await rightClickNode(wrapper, 'End');
        expect(wrapper.find('[data-test="node-menu-delete"]').text()).toContain('Delete 2 selected nodes');
        await wrapper.find('[data-test="node-menu-delete"]').trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="delete-nodes-titles"]').text()).toBe('Next, End');
        await wrapper.find('[data-test="delete-nodes-cancel"]').trigger('click');

        await rightClickNode(wrapper, 'Start');
        await wrapper.find('[data-test="node-menu-delete"]').trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="delete-nodes-titles"]').text()).toBe('Start');
    });

    it('stops at a failure, shows it, and reloads what is left', async () => {
        authoring.deleteDraftNode.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('Server down'));
        const wrapper = await mountEditor();
        await select(wrapper, 'Start', 'Next', 'End');
        await press('Delete');
        await wrapper.find('[data-test="delete-nodes-confirm"]').trigger('click');
        await flushPromises();

        expect(authoring.deleteDraftNode).toHaveBeenCalledTimes(2);
        expect(wrapper.emitted('nodeDeleted')).toEqual([['Start']]);
    });
});

describe('DialogueEditor moving nodes with the arrow keys (#193)', () => {
    // Vue Flow moves the selected nodes itself, through the nodes' v-model, without a drag-stop.
    async function moveSelected(wrapper, titles, dx) {
        const flow = wrapper.findComponent(VueFlowStub);
        flow.vm.$emit('update:nodes', flow.props('nodes').map((n) => titles.includes(n.id)
            ? { ...n, selected: true, position: { x: n.position.x + dx, y: n.position.y } }
            : { ...n, selected: false }));
        await flushPromises();
    }
    const release = async (key, target = document.body) => {
        target.dispatchEvent(new KeyboardEvent('keyup', { key, bubbles: true }));
        await flushPromises();
    };

    it('saves the moved nodes when the key is let go', async () => {
        const wrapper = await mountEditor();
        await moveSelected(wrapper, ['Next', 'End'], 20);
        await release('ArrowRight');

        expect(savedPositions()).toEqual([['Next', '220,0'], ['End', '420,0']]);
        expect(wrapper.emitted('dialogueSaved')).toHaveLength(1);

        // nothing more to save on the next key, since the stored positions now match
        await release('ArrowRight');
        expect(authoring.updateDraftNode).toHaveBeenCalledTimes(2);
    });

    it('saves nothing for other keys, while typing, or when nothing moved', async () => {
        const wrapper = await mountEditor();
        await release('ArrowLeft');
        await moveSelected(wrapper, ['Next'], 5);
        await release('a');
        const input = document.createElement('input');
        document.body.appendChild(input);
        await release('ArrowLeft', input);
        input.remove();
        expect(authoring.updateDraftNode).not.toHaveBeenCalled();
    });
});

describe('DialogueEditor search (#190)', () => {
    beforeEach(() => {
        currentNodes = [
            node('Start', 0, 'Welcome to the owl house.\n[[Hello|Next]]'),
            { ...node('Next', 200, 'Nothing here.'), tags: [{ key: 'title', value: 'Next' },
                { key: 'speaker', value: 'Imoen' }, { key: 'position', value: '200,0' },
                { key: 'comment', value: 'needs an owl joke' }] },
            node('End', 400, ''),
        ];
    });

    const search = async (wrapper, text) => {
        await wrapper.find('[data-test="graph-search-input"]').setValue(text);
        await flushPromises();
    };
    const marked = (wrapper, kind) =>
        wrapper.findAll(`[data-search="${kind}"]`).map((el) => el.element.closest('[data-node]').dataset.node);

    it('finds nodes by title, speaker, comment and text, ignoring case', async () => {
        const wrapper = await mountEditor();
        await search(wrapper, 'OWL');
        // text of Start, comment of Next
        expect(wrapper.find('[data-test="graph-search-count"]').text()).toBe('1 of 2');
        expect(marked(wrapper, 'current')).toEqual(['Start']);
        expect(marked(wrapper, 'match')).toEqual(['Next']);

        await search(wrapper, 'imoen');
        expect(marked(wrapper, 'current')).toEqual(['Next']);
        await search(wrapper, 'end');
        expect(marked(wrapper, 'current')).toEqual(['End']);
    });

    it('doesn\'t match the reply links or commands', async () => {
        const wrapper = await mountEditor();
        await search(wrapper, 'Hello');
        expect(wrapper.find('[data-test="graph-search-count"]').text()).toBe('None');
    });

    it('goes to the next and previous match, round, centring each', async () => {
        const wrapper = await mountEditor();
        await search(wrapper, 'owl');
        expect(flowView.setCenter).toHaveBeenLastCalledWith(50, 20, expect.anything());

        await wrapper.find('[data-test="graph-search-next"]').trigger('click');
        expect(wrapper.find('[data-test="graph-search-count"]').text()).toBe('2 of 2');
        expect(flowView.setCenter).toHaveBeenLastCalledWith(250, 20, expect.anything());

        await wrapper.find('[data-test="graph-search-input"]').trigger('keydown', { key: 'Enter' });
        expect(wrapper.find('[data-test="graph-search-count"]').text()).toBe('1 of 2');
        await wrapper.find('[data-test="graph-search-input"]').trigger('keydown', { key: 'Enter', shiftKey: true });
        expect(wrapper.find('[data-test="graph-search-count"]').text()).toBe('2 of 2');
    });

    it('doesn\'t select what it finds', async () => {
        const wrapper = await mountEditor();
        await search(wrapper, 'owl');
        expect(wrapper.findAll('[data-selected="true"]')).toHaveLength(0);
    });

    it('takes Ctrl+F only while the pointer is over the graph', async () => {
        const wrapper = await mountEditor({ attachTo: document.body });
        const input = wrapper.find('[data-test="graph-search-input"]').element;
        const ctrlF = () => document.body.dispatchEvent(new KeyboardEvent('keydown',
            { key: 'f', ctrlKey: true, bubbles: true, cancelable: true }));

        expect(ctrlF()).toBe(true); // not cancelled: the browser's own find
        expect(document.activeElement).not.toBe(input);

        await wrapper.trigger('mouseenter');
        expect(ctrlF()).toBe(false);
        expect(document.activeElement).toBe(input);
    });
});

