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

// The Import Scripts modal (#339): previews resulting dialogue names and which already exist,
// imports only what the author chose, and shows the server's results and per-file errors.

const authoring = {
    importScripts: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));
vi.mock('../../composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: ImportScriptsModal } = await import('./ImportScriptsModal.vue');

const DIALOGUES = [
    { name: 'onboarding/welcome', isDeleted: false },
    { name: 'onboarding/old', isDeleted: true },
    { name: 'main', isDeleted: false },
];

function dlb(name) {
    return new File(['title: Start\n---\nHi.\n===\n'], name, { type: 'text/plain' });
}

async function mountModal() {
    const wrapper = mount(ImportScriptsModal, {
        props: { dialogues: DIALOGUES },
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

describe('ImportScriptsModal', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('previews the resulting names and which dialogues already exist', async () => {
        const wrapper = await mountModal();
        await choose(wrapper, '/onboarding/', [dlb('welcome.dlb'), dlb('old.dlb'), dlb('new.dlb'), dlb('Bad name.dlb')]);
        expect(statuses(wrapper)).toEqual([
            ['onboarding/welcome', 'exists'],
            ['onboarding/old', 'deleted'],
            ['onboarding/new', 'new'],
            ['onboarding/Bad name', 'new'],
        ]);
        expect(wrapper.text()).toContain('not a valid dialogue name');
        expect(wrapper.find('[data-test="import-replace-warning"]').text()).toContain('2 of these dialogues already exist');
    });

    it('leaves existing dialogues out unless replacing is chosen', async () => {
        authoring.importScripts.mockResolvedValue([]);
        const wrapper = await mountModal();
        await choose(wrapper, 'onboarding', [dlb('welcome.dlb'), dlb('new.dlb')]);

        await wrapper.find('[data-test="import-submit"]').trigger('click');
        await flushPromises();
        const [slug, folder, files, options] = authoring.importScripts.mock.calls.at(-1);
        expect([slug, folder, options]).toEqual(['default-test', 'onboarding', { replace: false }]);
        expect(files.map((f) => f.name)).toEqual(['new.dlb']);
    });

    it('imports every file when replacing is chosen', async () => {
        authoring.importScripts.mockResolvedValue([]);
        const wrapper = await mountModal();
        await choose(wrapper, 'onboarding', [dlb('welcome.dlb'), dlb('new.dlb')]);
        await wrapper.find('[data-test="import-replace"]').setValue(true);

        await wrapper.find('[data-test="import-submit"]').trigger('click');
        await flushPromises();
        const [, , files, options] = authoring.importScripts.mock.calls.at(-1);
        expect(files.map((f) => f.name)).toEqual(['welcome.dlb', 'new.dlb']);
        expect(options).toEqual({ replace: true });
    });

    it('shows the results with their syntax errors and reports the import', async () => {
        const imported = [
            { fileName: 'new.dlb', dialogueName: 'new', replaced: false, parseErrors: ['Error in node Start: oops'] },
        ];
        authoring.importScripts.mockResolvedValue(imported);
        const wrapper = await mountModal();
        await choose(wrapper, '', [dlb('new.dlb')]);
        await wrapper.find('[data-test="import-submit"]').trigger('click');
        await flushPromises();

        const results = wrapper.find('[data-test="import-results"]');
        expect(results.text()).toContain('Imported 1 script(s)');
        expect(results.text()).toContain('Error in node Start: oops');
        expect(wrapper.emitted('imported')[0]).toEqual([imported]);
    });

    it('shows the server\'s problems per file when the import is refused', async () => {
        authoring.importScripts.mockRejectedValue({
            status: 400,
            message: 'Nothing was imported: 1 of 1 file(s) can\'t be imported.',
            errors: { 'new.dlb': ["line 2: header line 1: not a 'key: value' line"] },
        });
        const wrapper = await mountModal();
        await choose(wrapper, '', [dlb('new.dlb')]);
        await wrapper.find('[data-test="import-submit"]').trigger('click');
        await flushPromises();

        expect(wrapper.find('[data-test="import-error"]').text()).toContain('Nothing was imported');
        expect(wrapper.find('[data-test="import-rows"]').text()).toContain("not a 'key: value' line");
        expect(wrapper.emitted('imported')).toBeUndefined();
    });
});
