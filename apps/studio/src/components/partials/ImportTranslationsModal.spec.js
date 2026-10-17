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

// The Import Translations modal (#340): previews which dialogue each file belongs to and which
// already have a translation in the chosen language, refuses files without a dialogue up front,
// imports only what the author chose, and shows the server's results and per-file errors.

const authoring = {
    importTranslations: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));
vi.mock('../../composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: ImportTranslationsModal } = await import('./ImportTranslationsModal.vue');

const DIALOGUES = [
    { name: 'intro/welcome', isDeleted: false, translatedInto: ['nl'] },
    { name: 'intro/goodbye', isDeleted: false, translatedInto: ['pt'] },
    { name: 'intro/old', isDeleted: true, translatedInto: [] },
];

const LANGUAGES = [
    { code: 'nl', name: 'Dutch' },
    { code: 'pt', name: 'Portuguese' },
];

function json(name) {
    return new File(['{"Robin":{"Hello.":"Hallo."}}'], name, { type: 'application/json' });
}

async function mountModal(languages = LANGUAGES) {
    const wrapper = mount(ImportTranslationsModal, {
        props: { dialogues: DIALOGUES, languages },
        global: {
            provide: { state: ref({ selectedProject: { slug: 'default-test' } }) },
            stubs: { FontAwesomeIcon: true, teleport: true },
        },
    });
    await flushPromises();
    return wrapper;
}

async function choose(wrapper, folder, files) {
    await wrapper.find('[data-test="import-folder"]').setValue(folder);
    const input = wrapper.find('[data-test="import-files"]');
    Object.defineProperty(input.element, 'files', { value: files, configurable: true });
    await input.trigger('change');
}

const statuses = (wrapper) => wrapper.findAll('[data-test="import-rows"] > li')
    .map((li) => [li.find('.font-mono').text(), li.attributes('data-status')]);

const submit = (wrapper) => wrapper.find('[data-test="import-submit"]');

describe('ImportTranslationsModal', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('previews each file\'s dialogue and whether it has a translation in the chosen language', async () => {
        const wrapper = await mountModal();
        await choose(wrapper, '/intro/', [json('welcome.json'), json('goodbye.json')]);
        expect(statuses(wrapper)).toEqual([
            ['intro/welcome', 'exists'],
            ['intro/goodbye', 'new'],
        ]);
        expect(wrapper.find('[data-test="import-replace-warning"]').text()).toContain('1 of these dialogues already have a Dutch translation');

        await wrapper.find('[data-test="import-language"]').setValue('pt');
        expect(statuses(wrapper)).toEqual([
            ['intro/welcome', 'new'],
            ['intro/goodbye', 'exists'],
        ]);
    });

    it('can\'t import files without an existing dialogue', async () => {
        const wrapper = await mountModal();
        await choose(wrapper, 'intro', [json('goodbye.json'), json('missing.json'), json('old.json')]);
        expect(statuses(wrapper)).toEqual([
            ['intro/goodbye', 'new'],
            ['intro/missing', 'missing'],
            ['intro/old', 'deleted'],
        ]);
        expect(wrapper.find('[data-test="import-blocked"]').exists()).toBe(true);
        expect(submit(wrapper).attributes('disabled')).toBeDefined();
    });

    it('leaves existing translations out unless replacing is chosen', async () => {
        authoring.importTranslations.mockResolvedValue([]);
        const wrapper = await mountModal();
        await choose(wrapper, 'intro', [json('welcome.json'), json('goodbye.json')]);

        await submit(wrapper).trigger('click');
        await flushPromises();
        const [slug, language, folder, files, options] = authoring.importTranslations.mock.calls.at(-1);
        expect([slug, language, folder, options]).toEqual(['default-test', 'nl', 'intro', { replace: false }]);
        expect(files.map((f) => f.name)).toEqual(['goodbye.json']);
    });

    it('imports every file when replacing is chosen', async () => {
        authoring.importTranslations.mockResolvedValue([]);
        const wrapper = await mountModal();
        await choose(wrapper, 'intro', [json('welcome.json'), json('goodbye.json')]);
        await wrapper.find('[data-test="import-replace"]').setValue(true);

        await submit(wrapper).trigger('click');
        await flushPromises();
        const [, , , files, options] = authoring.importTranslations.mock.calls.at(-1);
        expect(files.map((f) => f.name)).toEqual(['welcome.json', 'goodbye.json']);
        expect(options).toEqual({ replace: true });
    });

    it('shows how each imported file covers its dialogue and reports the import', async () => {
        const imported = [
            { fileName: 'goodbye.json', dialogueName: 'intro/goodbye', replaced: false, unmatchedEntries: 2, untranslatedTerms: 1 },
            { fileName: 'welcome.json', dialogueName: 'intro/welcome', replaced: true, unmatchedEntries: 0, untranslatedTerms: 0 },
            { fileName: 'other.json', dialogueName: 'intro/other', replaced: false, unmatchedEntries: null, untranslatedTerms: null },
        ];
        authoring.importTranslations.mockResolvedValue(imported);
        const wrapper = await mountModal();
        await choose(wrapper, 'intro', [json('goodbye.json')]);
        await submit(wrapper).trigger('click');
        await flushPromises();

        const results = wrapper.find('[data-test="import-results"]').text();
        expect(results).toContain('Imported 3 translation(s) into Dutch');
        expect(results).toContain("2 entries don't match a current term");
        expect(results).toContain('1 term untranslated');
        expect(results).toContain('covers every term');
        expect(results).toContain('coverage unknown');
        expect(wrapper.emitted('imported')[0]).toEqual([imported]);
    });

    it('shows the server\'s problems per file when the import is refused', async () => {
        authoring.importTranslations.mockRejectedValue({
            status: 400,
            message: 'Nothing was imported: 1 of 1 file(s) can\'t be imported.',
            errors: { 'goodbye.json': ['"Hello." has no speaker: put it inside the group of the speaker it translates'] },
        });
        const wrapper = await mountModal();
        await choose(wrapper, 'intro', [json('goodbye.json')]);
        await submit(wrapper).trigger('click');
        await flushPromises();

        expect(wrapper.find('[data-test="import-error"]').text()).toContain('Nothing was imported');
        expect(wrapper.find('[data-test="import-rows"]').text()).toContain('has no speaker');
        expect(wrapper.emitted('imported')).toBeUndefined();
    });

    it('explains that a project without translation languages needs one first', async () => {
        const wrapper = await mountModal([]);
        expect(wrapper.find('[data-test="import-no-languages"]').text()).toContain('Configure Project');
        expect(wrapper.find('[data-test="import-files"]').exists()).toBe(false);
    });
});
