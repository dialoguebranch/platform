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
import { DLB_APP_MODE_DRAFT, DLB_APP_MODE_LIVE } from '@/StudioClientState.js';
import {
    SORT_DEFAULT, SORT_UPDATED_DESC,
} from '@/composables/dialogue-tree.js';

const client = {
    listDialogues: vi.fn(),
    getOngoingDialogue: vi.fn(),
    cancelDialogue: vi.fn(),
};

const authoringClient = {
    listDraftDialogues: vi.fn(),
    createDraftDialogue: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useClient: () => client,
    useAuthoringClient: () => authoringClient,
    getBrowserTimeZone: () => 'Europe/Lisbon',
}));

const { default: DialogueBrowser } = await import('./DialogueBrowser.vue');

function mountBrowser(options = {}) {
    const { mode = DLB_APP_MODE_DRAFT, openTabs = [] } = options;
    const state = ref({
        selectedProject: { slug: 'test-project' },
        mode,
    });

    const wrapper = mount(DialogueBrowser, {
        props: { openTabs },
        global: {
            provide: { state },
            stubs: {
                FontAwesomeIcon: true,
                DialogueTreeNode: {
                    name: 'DialogueTreeNode',
                    props: ['name', 'node', 'path', 'openFolders', 'sortMode', 'forceExpanded', 'depth'],
                    template: '<div class="stub-tree-node" :data-name="name" :data-file="node._file">{{ name }}</div>',
                },
                IconButton: {
                    name: 'IconButton',
                    props: ['icon', 'title', 'disabled'],
                    template: '<button type="button" :title="title" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
                },
                MainPagePanelHeader: {
                    template: '<header><slot /><slot name="buttons" /></header>',
                },
                MainPagePanelContainer: {
                    template: '<div class="panel-container"><slot /></div>',
                },
                teleport: true,
            },
        },
    });

    return { wrapper, state };
}

describe('DialogueBrowser', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        client.listDialogues.mockResolvedValue({ dialogueNames: [] });
        authoringClient.listDraftDialogues.mockResolvedValue([]);
    });

    it('lists draft dialogues in draft mode and emits hasUnpublishedChanges', async () => {
        authoringClient.listDraftDialogues.mockResolvedValue([
            { name: 'main', isNew: false, isChanged: true, isDeleted: false, nodeCount: 10 },
            { name: 'intro/welcome', isNew: true, isChanged: false, isDeleted: false, nodeCount: 4 },
        ]);

        const { wrapper } = mountBrowser({ mode: DLB_APP_MODE_DRAFT });
        await flushPromises();

        expect(authoringClient.listDraftDialogues).toHaveBeenCalledWith('test-project');
        expect(wrapper.emitted('hasUnpublishedChanges')).toBeTruthy();
        expect(wrapper.emitted('hasUnpublishedChanges').at(-1)).toEqual([true]);

        const renderedNodes = wrapper.findAll('.stub-tree-node');
        const renderedNames = renderedNodes.map((node) => node.attributes('data-name'));
        expect(renderedNames).toHaveLength(2);
        expect(renderedNames).toEqual(expect.arrayContaining(['main', 'intro']));
    });

    it('lists published dialogues in live mode', async () => {
        client.listDialogues.mockResolvedValue({
            dialogueNames: ['dialogue-a', 'dialogue-b'],
        });

        const { wrapper } = mountBrowser({ mode: DLB_APP_MODE_LIVE });
        await flushPromises();

        expect(client.listDialogues).toHaveBeenCalledWith({ projectSlug: 'test-project' });
        const renderedNodes = wrapper.findAll('.stub-tree-node');
        expect(renderedNodes.length).toBe(2);
    });

    it('updates search query and filters the tree', async () => {
        authoringClient.listDraftDialogues.mockResolvedValue([
            { name: 'alpha', isNew: false, isChanged: false, isDeleted: false },
            { name: 'beta', isNew: false, isChanged: false, isDeleted: false },
        ]);

        const { wrapper } = mountBrowser({ mode: DLB_APP_MODE_DRAFT });
        await flushPromises();

        const input = wrapper.find('input[placeholder="Filter dialogues…"]');
        expect(input.exists()).toBe(true);

        await input.setValue('alp');
        await flushPromises();

        const renderedNodes = wrapper.findAll('.stub-tree-node');
        expect(renderedNodes.some((n) => n.attributes('data-name') === 'alpha')).toBe(true);
        expect(renderedNodes.some((n) => n.attributes('data-name') === 'beta')).toBe(false);

        // Clearing search
        const clearButton = wrapper.find('button[title="Clear filter"]');
        expect(clearButton.exists()).toBe(true);
        await clearButton.trigger('click');
        await flushPromises();

        expect(input.element.value).toBe('');
    });

    it('resets authoring-only sort mode to default when switching out of draft mode', async () => {
        const { wrapper, state } = mountBrowser({ mode: DLB_APP_MODE_DRAFT });
        await flushPromises();

        const select = wrapper.find('select[title="Sort dialogues"]');
        await select.setValue(SORT_UPDATED_DESC);
        await flushPromises();

        expect(select.element.value).toBe(SORT_UPDATED_DESC);

        state.value.mode = DLB_APP_MODE_LIVE;
        await flushPromises();

        expect(select.element.value).toBe(SORT_DEFAULT);
    });

    it('handles new dialogue creation workflow', async () => {
        authoringClient.listDraftDialogues.mockResolvedValue([]);
        authoringClient.createDraftDialogue.mockResolvedValue({});

        const { wrapper } = mountBrowser({ mode: DLB_APP_MODE_DRAFT });
        await flushPromises();

        const newButton = wrapper.find('button[title="New Dialogue"]');
        expect(newButton.exists()).toBe(true);
        await newButton.trigger('click');

        const nameInput = wrapper.find('input[placeholder="folder/dialogue-name"]');
        expect(nameInput.exists()).toBe(true);

        await nameInput.setValue('stories/chapter1');
        const createButton = wrapper.find('button[title="Create"]');
        await createButton.trigger('click');
        await flushPromises();

        expect(authoringClient.createDraftDialogue).toHaveBeenCalledWith('test-project', 'stories/chapter1');
        expect(wrapper.find('input[placeholder="folder/dialogue-name"]').exists()).toBe(false);
    });

    it('can cancel new dialogue creation', async () => {
        const { wrapper } = mountBrowser({ mode: DLB_APP_MODE_DRAFT });
        await flushPromises();

        await wrapper.find('button[title="New Dialogue"]').trigger('click');
        expect(wrapper.find('input[placeholder="folder/dialogue-name"]').exists()).toBe(true);

        await wrapper.find('button[title="Cancel"]').trigger('click');
        expect(wrapper.find('input[placeholder="folder/dialogue-name"]').exists()).toBe(false);
    });

    it('checks ongoing dialogue in live mode and prompts resume', async () => {
        client.getOngoingDialogue.mockResolvedValue({
            dialogueName: 'resumable-dialogue',
            loggedDialogueId: 'log-123',
            secondsSinceLastEngagement: 45,
        });

        const { wrapper } = mountBrowser({ mode: DLB_APP_MODE_LIVE });
        await flushPromises();

        const ongoingButton = wrapper.find('button[title="Retrieve most recent ongoing (server-side) dialogue"]');
        expect(ongoingButton.exists()).toBe(true);
        await ongoingButton.trigger('click');
        await flushPromises();

        expect(client.getOngoingDialogue).toHaveBeenCalledWith({
            projectSlug: 'test-project',
            timeZone: 'Europe/Lisbon',
        });
        expect(wrapper.text()).toContain('resumable-dialogue');

        const continueButton = wrapper.findAll('button').find((b) => b.text().includes('Continue'));
        expect(continueButton).toBeDefined();
        await continueButton.trigger('click');
        expect(wrapper.emitted('resumeDialogue')).toBeTruthy();
        expect(wrapper.emitted('resumeDialogue')[0]).toEqual(['resumable-dialogue']);
    });
});
