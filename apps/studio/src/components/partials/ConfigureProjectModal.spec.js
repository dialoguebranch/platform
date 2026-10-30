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

// The Speakers tab of Configure Project (#395): staged like everything else, and sent as the
// complete new list on Save, only when it changed.

const authoring = {
    getProject: vi.fn(),
    updateProjectDraft: vi.fn(),
    findSpeakerReferences: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: ConfigureProjectModal } = await import('./ConfigureProjectModal.vue');

const PROJECT = {
    slug: 'p', draftDisplayName: 'P', draftDescription: '', sourceLanguageCode: 'en',
    sourceLanguageName: 'English', draftTranslationLanguages: [],
    draftSpeakers: [
        { name: 'Martin McOwl', gender: 'male', default: true },
        { name: 'Anna', gender: 'female', default: false },
    ],
};

async function openSpeakers() {
    const wrapper = mount(ConfigureProjectModal, {
        props: { projectSlug: 'p' },
        global: { stubs: { FontAwesomeIcon: true, teleport: true } },
    });
    await flushPromises();
    await wrapper.get('[data-test="tab-speakers"]').trigger('click');
    return wrapper;
}

const saveButton = (wrapper) => wrapper.findAll('button').find((b) => b.text().includes('Save Draft'));
const rows = (wrapper) => wrapper.findAll('[data-test="speaker-row"]');
const sentPayload = () => authoring.updateProjectDraft.mock.calls.at(-1)[1];

beforeEach(() => {
    vi.clearAllMocks();
    authoring.getProject.mockResolvedValue(structuredClone(PROJECT));
    authoring.updateProjectDraft.mockResolvedValue({ slug: 'p', draftDisplayName: 'P' });
});

describe('ConfigureProjectModal speakers (#395)', () => {
    it('lists the speakers with their gender and the default', async () => {
        const wrapper = await openSpeakers();
        expect(rows(wrapper).map((r) => r.find('td').text())).toEqual(['Martin McOwl', 'Anna']);
        expect(rows(wrapper)[1].get('[data-test="speaker-gender"]').element.value).toBe('female');
        expect(rows(wrapper)[0].get('[data-test="speaker-default"]').attributes('title'))
            .toContain('The default speaker');
    });

    it('sends no speakers when they didn\'t change', async () => {
        const wrapper = await openSpeakers();
        expect(saveButton(wrapper).attributes('disabled')).toBeDefined();
    });

    it('adds a speaker and sends the complete new list', async () => {
        const wrapper = await openSpeakers();
        await wrapper.get('[data-test="new-speaker-name"]').setValue('Narrator');
        await wrapper.get('[data-test="add-speaker"]').trigger('click');
        await saveButton(wrapper).trigger('click');
        await flushPromises();

        expect(sentPayload().speakers).toEqual([
            { name: 'Martin McOwl', gender: 'male', default: true },
            { name: 'Anna', gender: 'female', default: false },
            { name: 'Narrator', gender: 'undefined', default: false },
        ]);
    });

    it('refuses a name already in the list before adding it', async () => {
        const wrapper = await openSpeakers();
        await wrapper.get('[data-test="new-speaker-name"]').setValue('Anna');
        await wrapper.get('[data-test="add-speaker"]').trigger('click');
        expect(wrapper.get('[data-test="add-speaker-error"]').text()).toContain('already');
        expect(rows(wrapper)).toHaveLength(2);
    });

    it('keeps at most one default, and lets the default be cleared', async () => {
        const wrapper = await openSpeakers();
        await rows(wrapper)[1].get('[data-test="speaker-default"]').trigger('click');
        await saveButton(wrapper).trigger('click');
        await flushPromises();
        expect(sentPayload().speakers.map((s) => s.default)).toEqual([false, true]);

        await rows(wrapper)[1].get('[data-test="speaker-default"]').trigger('click');
        await saveButton(wrapper).trigger('click');
        await flushPromises();
        expect(sentPayload().speakers.map((s) => s.default)).toEqual([false, false]);
    });

    it('changes a gender and removes a speaker', async () => {
        const wrapper = await openSpeakers();
        await rows(wrapper)[0].get('[data-test="speaker-gender"]').setValue('undefined');
        await rows(wrapper)[1].get('[data-test="speaker-remove"]').trigger('click');
        await saveButton(wrapper).trigger('click');
        await flushPromises();
        expect(sentPayload().speakers).toEqual([
            { name: 'Martin McOwl', gender: 'undefined', default: true },
        ]);
    });

    it('shows the server\'s reason in the Speakers tab when it refuses them', async () => {
        authoring.updateProjectDraft.mockRejectedValue(Object.assign(new Error('Bad Request'), {
            status: 400,
            fieldErrors: [{ field: 'speakers', message: 'The speaker \'Anna\' is defined more than once.' }],
        }));
        const wrapper = await openSpeakers();
        await rows(wrapper)[1].get('[data-test="speaker-remove"]').trigger('click');
        await saveButton(wrapper).trigger('click');
        await flushPromises();
        expect(wrapper.get('[data-test="speaker-error"]').text()).toContain('more than once');
        expect(wrapper.find('[data-test="speaker-row"]').exists()).toBe(true);
    });
});

describe('ConfigureProjectModal speaker renames (#398)', () => {
    async function rename(wrapper, index, name) {
        await rows(wrapper)[index].get('[data-test="speaker-rename"]').trigger('click');
        await wrapper.get('[data-test="speaker-rename-input"]').setValue(name);
        await wrapper.get('[data-test="speaker-rename-input"]').element.closest('form')
            .dispatchEvent(new Event('submit'));
        await flushPromises();
    }

    it('stages a rename, confirms what it changes, and sends previousName', async () => {
        authoring.findSpeakerReferences.mockResolvedValue({ nodes: 4, dialogues: 2, translations: 3 });
        const wrapper = await openSpeakers();
        await rename(wrapper, 1, 'Annie');
        expect(rows(wrapper)[1].get('[data-test="speaker-was"]').text()).toContain('was Anna');

        await saveButton(wrapper).trigger('click');
        await flushPromises();
        expect(authoring.updateProjectDraft).not.toHaveBeenCalled();
        expect(authoring.findSpeakerReferences).toHaveBeenCalledWith('p', 'Anna');
        expect(wrapper.get('[data-test="speaker-rename-confirmation"]').text())
            .toContain('Renaming "Anna" to "Annie" changes 4 nodes in 2 dialogues, and 3 translations.');

        await wrapper.get('[data-test="speaker-rename-confirm"]').trigger('click');
        await flushPromises();
        expect(sentPayload().speakers[1]).toEqual(
            { name: 'Annie', previousName: 'Anna', gender: 'female', default: false });
        expect(sentPayload().speakers[0]).not.toHaveProperty('previousName');
        expect(wrapper.emitted('saved').at(-1)[0].renamedSpeakers).toBe(true);
    });

    it('goes back without saving', async () => {
        authoring.findSpeakerReferences.mockResolvedValue({ nodes: 1, dialogues: 1, translations: 0 });
        const wrapper = await openSpeakers();
        await rename(wrapper, 0, 'Martin');
        await saveButton(wrapper).trigger('click');
        await flushPromises();
        await wrapper.get('[data-test="speaker-rename-back"]').trigger('click');

        expect(wrapper.find('[data-test="speaker-rename-confirmation"]').exists()).toBe(false);
        expect(authoring.updateProjectDraft).not.toHaveBeenCalled();
    });

    it('refuses a name another speaker has', async () => {
        const wrapper = await openSpeakers();
        await rename(wrapper, 0, 'Anna');
        expect(wrapper.get('[data-test="speaker-rename-error"]').text()).toContain('already');
        expect(rows(wrapper)[0].find('[data-test="speaker-was"]').exists()).toBe(false);
    });

    it('renaming back to the original name is no rename', async () => {
        const wrapper = await openSpeakers();
        await rename(wrapper, 1, 'Annie');
        await rename(wrapper, 1, 'Anna');
        expect(rows(wrapper)[1].find('[data-test="speaker-was"]').exists()).toBe(false);
        expect(saveButton(wrapper).attributes('disabled')).toBeDefined();
    });
});
