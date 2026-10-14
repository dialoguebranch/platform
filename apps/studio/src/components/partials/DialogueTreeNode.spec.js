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

const authoring = {
    exportDialogue: vi.fn(),
    restoreDraftDialogue: vi.fn(),
    findDialogueReferences: vi.fn(),
    renameDraftDialogue: vi.fn(),
    deleteDraftDialogue: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

vi.mock('@/composables/download-blob.js', () => ({ downloadBlob: vi.fn() }));

const { default: DialogueTreeNode } = await import('./DialogueTreeNode.vue');
const { downloadBlob } = await import('@/composables/download-blob.js');
const { errorToast } = await import('@/composables/error-toast.js');

function mountNode(props = {}, stateMode = DLB_APP_MODE_DRAFT) {
    const defaultProps = {
        name: 'test-dialogue',
        node: {
            _file: 'folder/test-dialogue',
            _isPublished: true,
            _isNew: false,
            _isChanged: false,
            _isDeleted: false,
            _updatedAt: '2026-10-14T12:00:00Z',
            _nodeCount: 5,
        },
        path: 'folder/test-dialogue',
        openFolders: {},
        sortMode: 'default',
        forceExpanded: false,
        depth: 0,
        ...props,
    };

    return mount(DialogueTreeNode, {
        props: defaultProps,
        global: {
            provide: {
                state: ref({
                    selectedProject: { slug: 'test-project' },
                    mode: stateMode,
                }),
            },
            stubs: {
                FontAwesomeIcon: true,
                DialogueTreeNode: true,
                teleport: true,
            },
        },
    });
}

describe('DialogueTreeNode', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders a dialogue file row and emits openDialogue when clicked', async () => {
        const wrapper = mountNode();
        expect(wrapper.text()).toContain('test-dialogue');

        const openButton = wrapper.find('button[title="Open dialogue"]');
        expect(openButton.exists()).toBe(true);
        await openButton.trigger('click');

        expect(wrapper.emitted('openDialogue')).toBeTruthy();
        expect(wrapper.emitted('openDialogue')[0]).toEqual(['folder/test-dialogue']);
    });

    it('renders a folder row and emits toggleFolder when clicked', async () => {
        const folderNode = {
            _children: {
                'child-file': { _file: 'parent/child-file', _isPublished: true },
            },
        };

        const wrapper = mountNode({
            name: 'parent',
            node: folderNode,
            path: 'parent',
            openFolders: { parent: false },
        });

        expect(wrapper.text()).toContain('parent');
        const folderName = wrapper.findAll('span').find((span) => span.text().trim() === 'parent');
        expect(folderName).toBeDefined();
        await folderName.trigger('click');

        expect(wrapper.emitted('toggleFolder')).toBeTruthy();
        expect(wrapper.emitted('toggleFolder')[0]).toEqual(['parent']);
    });

    it('shows deleted state and allows restoring a deleted draft dialogue', async () => {
        authoring.restoreDraftDialogue.mockResolvedValue({});

        const wrapper = mountNode({
            node: {
                _file: 'folder/deleted-dialogue',
                _isPublished: true,
                _isNew: false,
                _isChanged: false,
                _isDeleted: true,
            },
        });

        const restoreButton = wrapper.find('button[title="Restore (undo deletion)"]');
        expect(restoreButton.exists()).toBe(true);

        await restoreButton.trigger('click');
        await flushPromises();

        expect(authoring.restoreDraftDialogue).toHaveBeenCalledWith('test-project', 'folder/deleted-dialogue');
        expect(wrapper.emitted('dialoguesChanged')).toBeTruthy();
    });

    it('handles rename initiation and submission without cross-references', async () => {
        authoring.findDialogueReferences.mockResolvedValue([]);
        authoring.renameDraftDialogue.mockResolvedValue({});

        const wrapper = mountNode();
        const renameButton = wrapper.find('button[title="Rename dialogue"]');
        expect(renameButton.exists()).toBe(true);

        await renameButton.trigger('click');
        expect(wrapper.find('input[type="text"]').exists()).toBe(true);

        const input = wrapper.find('input[type="text"]');
        await input.setValue('renamed-dialogue');

        const submitButton = wrapper.find('button[title="Save"]');
        await submitButton.trigger('click');
        await flushPromises();

        expect(authoring.findDialogueReferences).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        expect(authoring.renameDraftDialogue).toHaveBeenCalledWith('test-project', 'folder/test-dialogue', 'folder/renamed-dialogue', false);
        expect(wrapper.emitted('dialoguesChanged')).toBeTruthy();
    });

    it('prompts confirmation when renaming dialogue with references and updates references when confirmed', async () => {
        authoring.findDialogueReferences.mockResolvedValue([
            { dialogueName: 'other-dialogue', nodeTitle: 'Step1', snippet: '[[Go on|folder/test-dialogue.Start]]' },
        ]);
        authoring.renameDraftDialogue.mockResolvedValue({});

        const wrapper = mountNode();
        await wrapper.find('button[title="Rename dialogue"]').trigger('click');

        const input = wrapper.find('input[type="text"]');
        await input.setValue('renamed-dialogue');
        await wrapper.find('button[title="Save"]').trigger('click');
        await flushPromises();

        expect(authoring.findDialogueReferences).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        expect(wrapper.text()).toContain('Update all references');

        const updateButton = wrapper.findAll('button').find((b) => b.text() === 'Update all references');
        expect(updateButton).toBeDefined();
        await updateButton.trigger('click');
        await flushPromises();

        expect(authoring.renameDraftDialogue).toHaveBeenCalledWith('test-project', 'folder/test-dialogue', 'folder/renamed-dialogue', true);
        expect(wrapper.emitted('dialoguesChanged')).toBeTruthy();
    });
});

describe('DialogueTreeNode — Export as .dlb (#341)', () => {
    const exportButton = (wrapper) => wrapper.find('button[title="Export as .dlb"]');

    beforeEach(() => {
        vi.clearAllMocks();
        errorToast.value = null;
    });

    it('offers the export on a dialogue in Authoring Mode', () => {
        expect(exportButton(mountNode()).exists()).toBe(true);
    });

    it('does not offer it in Live Mode, where there is no draft to export', () => {
        expect(exportButton(mountNode({}, DLB_APP_MODE_LIVE)).exists()).toBe(false);
    });

    it('does not offer it on a dialogue pending deletion', () => {
        const wrapper = mountNode({ node: { _file: 'folder/test-dialogue', _isDeleted: true } });
        expect(exportButton(wrapper).exists()).toBe(false);
    });

    it('does not offer it on a folder', () => {
        const wrapper = mountNode({ name: 'folder', node: { _children: {} }, path: 'folder' });
        expect(exportButton(wrapper).exists()).toBe(false);
    });

    it('downloads the dialogue, named after its last name segment and not its folder', async () => {
        const blob = new Blob(['title: Start\n---\nHi.\n===\n']);
        authoring.exportDialogue.mockResolvedValue(blob);
        const wrapper = mountNode();

        await exportButton(wrapper).trigger('click');
        await flushPromises();

        expect(authoring.exportDialogue).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        expect(downloadBlob).toHaveBeenCalledWith(blob, 'test-dialogue.dlb');
        expect(errorToast.value).toBeNull();
    });

    it('names the file after a dialogue at the project root as it is', async () => {
        authoring.exportDialogue.mockResolvedValue(new Blob(['']));
        const wrapper = mountNode({ node: { _file: 'main', _isDeleted: false } });

        await exportButton(wrapper).trigger('click');
        await flushPromises();

        expect(downloadBlob.mock.calls[0][1]).toBe('main.dlb');
    });

    it('shows an error when the export fails, and downloads nothing', async () => {
        authoring.exportDialogue.mockRejectedValue(Object.assign(new Error('Dialogue not found: x'),
            { status: 404, statusText: 'Not Found' }));
        const wrapper = mountNode();

        await exportButton(wrapper).trigger('click');
        await flushPromises();

        expect(downloadBlob).not.toHaveBeenCalled();
        expect(errorToast.value).toBeTruthy();
    });

    it('ignores a second click while the export is still running', async () => {
        let finish;
        authoring.exportDialogue.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
        const wrapper = mountNode();

        await exportButton(wrapper).trigger('click');
        expect(exportButton(wrapper).attributes('disabled')).toBeDefined();
        await exportButton(wrapper).trigger('click');
        expect(authoring.exportDialogue).toHaveBeenCalledTimes(1);

        finish(new Blob(['']));
        await flushPromises();
        expect(exportButton(wrapper).attributes('disabled')).toBeUndefined();
        expect(downloadBlob).toHaveBeenCalledTimes(1);
    });
});
