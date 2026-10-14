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

// The Translation Editor's stale entries (#375): entries of a language's translation that match no
// term of the dialogue on speaker and text are counted, listed per language, and can be deleted
// (one, or all of a language), which saves that language's translation without them.

const authoring = {
    getProject: vi.fn(),
    listTranslatableTerms: vi.fn(),
    getDraftTranslation: vi.fn(),
    updateDraftTranslation: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: TranslationEditor } = await import('./TranslationEditor.vue');

const TRANSLATIONS = {
    nl: {
        'Martin McOwl': { 'Hello.': 'Hallo.', 'Old line.': 'Oude regel.' },
        Ghost: { 'Boo.': 'Boe.' },
    },
    pt: {
        'Martin McOwl': { 'Hello.': 'Olá.', female_addressee: { 'Gone.': 'Foi.' } },
    },
};

function setUp(translations = TRANSLATIONS) {
    authoring.getProject.mockResolvedValue({
        draftTranslationLanguages: [
            { translationLanguageCode: 'nl', translationLanguageName: 'Dutch', isDeleted: false },
            { translationLanguageCode: 'pt', translationLanguageName: 'Portuguese', isDeleted: false },
        ],
    });
    authoring.listTranslatableTerms.mockResolvedValue([
        { speaker: 'Martin McOwl', term: 'Hello.' },
        { speaker: '_user', term: 'Fine.' },
    ]);
    authoring.getDraftTranslation.mockImplementation(async (slug, dialogue, language) =>
        (translations[language] ? { content: JSON.stringify(translations[language]) } : null));
    authoring.updateDraftTranslation.mockResolvedValue({});
}

async function mountEditor() {
    const wrapper = mount(TranslationEditor, {
        props: { dialogueName: 'basic' },
        global: {
            provide: { state: ref({ selectedProject: { slug: 'default-test' } }) },
            stubs: { FontAwesomeIcon: true, teleport: true },
        },
    });
    await flushPromises();
    return wrapper;
}

const staleButton = (wrapper) => wrapper.find('[data-test="stale-entries-button"]');

async function openStale(wrapper) {
    await staleButton(wrapper).trigger('click');
}

const savedContent = () => JSON.parse(authoring.updateDraftTranslation.mock.calls.at(-1)[3]);

describe('TranslationEditor stale entries', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        setUp();
    });

    it('counts the stale entries of every translation language', async () => {
        const wrapper = await mountEditor();
        expect(staleButton(wrapper).text()).toContain('Stale entries (3)');
    });

    it('shows no button when there are no stale entries', async () => {
        setUp({ nl: { 'Martin McOwl': { 'Hello.': 'Hallo.' } } });
        const wrapper = await mountEditor();
        expect(staleButton(wrapper).exists()).toBe(false);
    });

    it('lists the stale entries per language, with their speaker and contexts', async () => {
        const wrapper = await mountEditor();
        await openStale(wrapper);
        const dutch = wrapper.find('[data-test="stale-section-nl"]');
        expect(dutch.text()).toContain('Dutch (2)');
        expect(dutch.text()).toContain('Old line.');
        expect(dutch.text()).toContain('Ghost');
        const portuguese = wrapper.find('[data-test="stale-section-pt"]');
        expect(portuguese.text()).toContain('female_addressee');
        expect(portuguese.text()).toContain('Gone.');
    });

    it('deletes one entry after confirming, saving that language without it', async () => {
        const wrapper = await mountEditor();
        await openStale(wrapper);
        await wrapper.find('[data-test="stale-section-nl"] [data-test="stale-delete"]').trigger('click');
        expect(authoring.updateDraftTranslation).not.toHaveBeenCalled();
        expect(wrapper.find('[data-test="stale-confirm-text"]').text()).toContain('this Dutch entry');

        await wrapper.find('[data-test="stale-confirm"]').trigger('click');
        await flushPromises();
        const [slug, dialogue, language] = authoring.updateDraftTranslation.mock.calls.at(-1);
        expect([slug, dialogue, language]).toEqual(['default-test', 'basic', 'nl']);
        expect(savedContent()).toEqual({
            'Martin McOwl': { 'Hello.': 'Hallo.' },
            Ghost: { 'Boo.': 'Boe.' },
        });
        expect(wrapper.emitted('dialogueSaved')).toHaveLength(1);
        expect(staleButton(wrapper).text()).toContain('Stale entries (2)');
    });

    it('deletes all of a language\'s stale entries after confirming', async () => {
        const wrapper = await mountEditor();
        await openStale(wrapper);
        await wrapper.find('[data-test="stale-section-pt"] [data-test="stale-delete-all"]').trigger('click');
        await wrapper.find('[data-test="stale-confirm"]').trigger('click');
        await flushPromises();
        expect(authoring.updateDraftTranslation.mock.calls.at(-1)[2]).toBe('pt');
        expect(savedContent()).toEqual({ 'Martin McOwl': { 'Hello.': 'Olá.' } });
        expect(wrapper.find('[data-test="stale-section-pt"]').exists()).toBe(false);
    });

    it('keeps the entries when the deletion is cancelled', async () => {
        const wrapper = await mountEditor();
        await openStale(wrapper);
        await wrapper.find('[data-test="stale-delete-all"]').trigger('click');
        await wrapper.findAll('button').find((b) => b.text() === 'Cancel').trigger('click');
        expect(authoring.updateDraftTranslation).not.toHaveBeenCalled();
        expect(wrapper.findAll('[data-test="stale-entry"]')).toHaveLength(3);
    });

    it('keeps the entries when saving fails', async () => {
        authoring.updateDraftTranslation.mockRejectedValue({ status: 400, message: 'Nope' });
        const wrapper = await mountEditor();
        await openStale(wrapper);
        await wrapper.find('[data-test="stale-section-nl"] [data-test="stale-delete-all"]').trigger('click');
        await wrapper.find('[data-test="stale-confirm"]').trigger('click');
        await flushPromises();
        expect(staleButton(wrapper).text()).toContain('Stale entries (3)');
        expect(wrapper.emitted('dialogueSaved')).toBeUndefined();
    });
});
