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
    discardDraftDialogueChanges: vi.fn(),
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

const menuButton = (wrapper) => wrapper.find('[data-test="row-menu-button"]');

async function openMenu(wrapper) {
    await menuButton(wrapper).trigger('click');
    await flushPromises();
}

// The row menu's item, or a non-existing wrapper when the row has no menu.
async function menuItem(wrapper, test) {
    if (menuButton(wrapper).exists()) await openMenu(wrapper);
    return wrapper.find(`[data-test="${test}"]`);
}

async function choose(wrapper, test) {
    await (await menuItem(wrapper, test)).trigger('click');
    await flushPromises();
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
        await choose(wrapper, 'menu-rename');
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
        await choose(wrapper, 'menu-rename');

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
    const offersExport = async (wrapper) => (await menuItem(wrapper, 'menu-export')).exists();

    beforeEach(() => {
        vi.clearAllMocks();
        errorToast.value = null;
    });

    it('offers the export on a dialogue in Authoring Mode', async () => {
        expect(await offersExport(mountNode())).toBe(true);
    });

    it('does not offer it in Live Mode, where there is no draft to export', async () => {
        expect(await offersExport(mountNode({}, DLB_APP_MODE_LIVE))).toBe(false);
    });

    it('does not offer it on a dialogue pending deletion', async () => {
        const wrapper = mountNode({ node: { _file: 'folder/test-dialogue', _isDeleted: true } });
        expect(await offersExport(wrapper)).toBe(false);
    });

    it('does not offer it on a folder', async () => {
        const wrapper = mountNode({ name: 'folder', node: { _children: {} }, path: 'folder' });
        expect(await offersExport(wrapper)).toBe(false);
    });

    it('downloads the dialogue, named after its last name segment and not its folder', async () => {
        const blob = new Blob(['title: Start\n---\nHi.\n===\n']);
        authoring.exportDialogue.mockResolvedValue(blob);
        const wrapper = mountNode();

        await choose(wrapper, 'menu-export');

        expect(authoring.exportDialogue).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        expect(downloadBlob).toHaveBeenCalledWith(blob, 'test-dialogue.dlb');
        expect(errorToast.value).toBeNull();
    });

    it('names the file after a dialogue at the project root as it is', async () => {
        authoring.exportDialogue.mockResolvedValue(new Blob(['']));
        const wrapper = mountNode({ node: { _file: 'main', _isDeleted: false } });

        await choose(wrapper, 'menu-export');

        expect(downloadBlob.mock.calls[0][1]).toBe('main.dlb');
    });

    it('shows an error when the export fails, and downloads nothing', async () => {
        authoring.exportDialogue.mockRejectedValue(Object.assign(new Error('Dialogue not found: x'),
            { status: 404, statusText: 'Not Found' }));
        const wrapper = mountNode();

        await choose(wrapper, 'menu-export');

        expect(downloadBlob).not.toHaveBeenCalled();
        expect(errorToast.value).toBeTruthy();
    });

    it('ignores a second click while the export is still running', async () => {
        let finish;
        authoring.exportDialogue.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
        const wrapper = mountNode();

        const spinner = () => menuButton(wrapper).find('font-awesome-icon-stub').attributes('icon');

        await choose(wrapper, 'menu-export');
        expect(spinner()).toContain('circle-notch');
        await choose(wrapper, 'menu-export');
        expect(authoring.exportDialogue).toHaveBeenCalledTimes(1);

        finish(new Blob(['']));
        await flushPromises();
        expect(spinner()).toContain('ellipsis');
        expect(downloadBlob).toHaveBeenCalledTimes(1);
    });
});

// Discarding a changed dialogue's draft changes (#363).
describe('DialogueTreeNode discard changes', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    const changedNode = (extra = {}) => ({
        _file: 'folder/test-dialogue', _isPublished: true, _isNew: false, _isChanged: true,
        _isDeleted: false, _previousPublishedName: null, ...extra,
    });

    it('is only offered for a changed dialogue that was published, in Authoring Mode', async () => {
        const offered = async (wrapper) => (await menuItem(wrapper, 'menu-discard')).exists();
        expect(await offered(mountNode({ node: changedNode() }))).toBe(true);
        expect(await offered(mountNode())).toBe(false);
        expect(await offered(mountNode({ node: changedNode({ _isNew: true, _isPublished: false }) }))).toBe(false);
        expect(await offered(mountNode({ node: changedNode({ _isDeleted: true }) }))).toBe(false);
        expect(await offered(mountNode({ node: changedNode() }, DLB_APP_MODE_LIVE))).toBe(false);
    });

    it('asks for confirmation, warning about links in other dialogues', async () => {
        const wrapper = mountNode({ node: changedNode() });
        await choose(wrapper, 'menu-discard');
        const prompt = wrapper.find('[data-test="discard-prompt"]');
        expect(prompt.text()).toContain('folder/test-dialogue');
        expect(prompt.text()).toContain('those links will no longer match');
        expect(prompt.find('[data-test="discard-renamed"]').exists()).toBe(false);
        expect(authoring.discardDraftDialogueChanges).not.toHaveBeenCalled();
    });

    it('mentions the published name a renamed dialogue gets back', async () => {
        const wrapper = mountNode({ node: changedNode({ _previousPublishedName: 'folder/original' }) });
        await choose(wrapper, 'menu-discard');
        expect(wrapper.find('[data-test="discard-renamed"]').text()).toContain('folder/original');
    });

    it('discards after confirming and reports both names as replaced', async () => {
        authoring.discardDraftDialogueChanges.mockResolvedValue(
            { dialogueName: 'folder/original', discardedName: 'folder/test-dialogue' });
        const wrapper = mountNode({ node: changedNode({ _previousPublishedName: 'folder/original' }) });
        await choose(wrapper, 'menu-discard');
        await wrapper.find('[data-test="discard-confirm"]').trigger('click');
        await flushPromises();
        expect(authoring.discardDraftDialogueChanges).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        expect(wrapper.emitted('dialoguesReplaced')[0]).toEqual([['folder/test-dialogue', 'folder/original']]);
        expect(wrapper.find('[data-test="discard-prompt"]').exists()).toBe(false);
    });

    it('shows the server\'s error when discarding is refused', async () => {
        authoring.discardDraftDialogueChanges.mockRejectedValue(
            { status: 409, message: 'another dialogue now has that name' });
        const wrapper = mountNode({ node: changedNode() });
        await choose(wrapper, 'menu-discard');
        await wrapper.find('[data-test="discard-confirm"]').trigger('click');
        await flushPromises();
        expect(wrapper.emitted('dialoguesReplaced')).toBeUndefined();
        expect(errorToast.value?.message ?? JSON.stringify(errorToast.value)).toContain('another dialogue now has that name');
    });
});

// The row shows only its launch button; other actions are in a context menu (#389).
describe('DialogueTreeNode row menu', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    const rowButtons = (wrapper) => wrapper.find('[data-test="dialogue-row"]').findAll(':scope > button')
        .map((b) => b.attributes('title'));

    it('shows only the menu button and the launch button on a row in Authoring Mode', () => {
        expect(rowButtons(mountNode())).toEqual(['More actions', 'Open dialogue']);
    });

    it('lists the actions that apply, in order', async () => {
        const wrapper = mountNode({ node: { _file: 'folder/test-dialogue', _isChanged: true, _isNew: false } });
        await openMenu(wrapper);
        expect(wrapper.findAll('[role="menuitem"]').map((b) => b.text()))
            .toEqual(['Open', 'Rename…', 'Export as .dlb', 'Discard changes…', 'Delete…']);
    });

    it('opens on a right-click on the row, at the pointer', async () => {
        const wrapper = mountNode();
        const event = new MouseEvent('contextmenu', { clientX: 30, clientY: 40, bubbles: true, cancelable: true });
        wrapper.find('[data-test="dialogue-row"]').element.dispatchEvent(event);
        await flushPromises();
        expect(event.defaultPrevented).toBe(true);
        expect(wrapper.find('[data-test="context-menu"]').exists()).toBe(true);
    });

    it('opens the dialogue from the menu', async () => {
        const wrapper = mountNode();
        await choose(wrapper, 'menu-open');
        expect(wrapper.emitted('openDialogue')[0]).toEqual(['folder/test-dialogue']);
        expect(wrapper.find('[data-test="context-menu"]').exists()).toBe(false);
    });

    it('has no menu in Live Mode, and leaves the right-click alone', async () => {
        const wrapper = mountNode({}, DLB_APP_MODE_LIVE);
        expect(rowButtons(wrapper)).toEqual(['Open dialogue']);
        const event = new MouseEvent('contextmenu', { bubbles: true, cancelable: true });
        wrapper.find('[data-test="dialogue-row"]').element.dispatchEvent(event);
        await flushPromises();
        expect(event.defaultPrevented).toBe(false);
        expect(wrapper.find('[data-test="context-menu"]').exists()).toBe(false);
    });

    it('keeps Restore as the only button on a dialogue pending deletion, without a menu', () => {
        const wrapper = mountNode({ node: { _file: 'folder/test-dialogue', _isDeleted: true } });
        expect(rowButtons(wrapper)).toEqual(['Restore (undo deletion)']);
    });
});

// Deleting a dialogue from its row menu (#390).
describe('DialogueTreeNode delete', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        errorToast.value = null;
    });

    it('offers Delete… as a destructive item, after a separator', async () => {
        const wrapper = mountNode();
        const item = await menuItem(wrapper, 'menu-delete');
        expect(item.exists()).toBe(true);
        expect(item.classes()).toContain('text-red-dark');
        expect(wrapper.find('[role="separator"]').exists()).toBe(true);
    });

    it('asks for confirmation, without links from other dialogues', async () => {
        authoring.findDialogueReferences.mockResolvedValue([
            { dialogueName: 'folder/test-dialogue', nodeTitle: 'Start', snippet: '[[Again|folder/test-dialogue.Start]]' },
        ]);
        const wrapper = mountNode();
        await choose(wrapper, 'menu-delete');
        expect(authoring.findDialogueReferences).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        const prompt = wrapper.find('[data-test="delete-prompt"]');
        expect(prompt.text()).toContain('can be restored until the project is published next');
        expect(prompt.find('[data-test="delete-references"]').exists()).toBe(false);
        expect(authoring.deleteDraftDialogue).not.toHaveBeenCalled();
    });

    it('warns about the replies in other dialogues that link to it', async () => {
        authoring.findDialogueReferences.mockResolvedValue([
            { dialogueName: 'other', nodeTitle: 'A', snippet: '[[Go|folder/test-dialogue.Start]]' },
            { dialogueName: 'other', nodeTitle: 'B', snippet: '[[Go|folder/test-dialogue.Start]]' },
            { dialogueName: 'third', nodeTitle: 'C', snippet: '[[Go|folder/test-dialogue.Start]]' },
        ]);
        const wrapper = mountNode();
        await choose(wrapper, 'menu-delete');
        expect(wrapper.find('[data-test="delete-references"]').text())
            .toMatch(/3 replies\s+in 2 other dialogues\s+link to it; those links will break/);
    });

    it('deletes after confirming and reports the deleted dialogue', async () => {
        authoring.findDialogueReferences.mockResolvedValue([]);
        authoring.deleteDraftDialogue.mockResolvedValue();
        const wrapper = mountNode();
        await choose(wrapper, 'menu-delete');
        await wrapper.find('[data-test="delete-confirm"]').trigger('click');
        await flushPromises();
        expect(authoring.deleteDraftDialogue).toHaveBeenCalledWith('test-project', 'folder/test-dialogue');
        expect(wrapper.emitted('dialogueDeleted')[0]).toEqual(['folder/test-dialogue']);
        expect(wrapper.find('[data-test="delete-prompt"]').exists()).toBe(false);
    });

    it('deletes nothing when cancelled', async () => {
        authoring.findDialogueReferences.mockResolvedValue([]);
        const wrapper = mountNode();
        await choose(wrapper, 'menu-delete');
        await wrapper.find('[data-test="delete-prompt"]').findAll('button').find((b) => b.text() === 'Cancel').trigger('click');
        expect(authoring.deleteDraftDialogue).not.toHaveBeenCalled();
        expect(wrapper.find('[data-test="delete-prompt"]').exists()).toBe(false);
    });

    it('shows the error when deleting fails', async () => {
        authoring.findDialogueReferences.mockResolvedValue([]);
        authoring.deleteDraftDialogue.mockRejectedValue({ status: 404, message: 'Dialogue not found: x' });
        const wrapper = mountNode();
        await choose(wrapper, 'menu-delete');
        await wrapper.find('[data-test="delete-confirm"]').trigger('click');
        await flushPromises();
        expect(wrapper.emitted('dialogueDeleted')).toBeUndefined();
        expect(errorToast.value).toBeTruthy();
    });
});
