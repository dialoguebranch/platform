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

// The verify step lists warnings (orphaned nodes, undefined speakers, #397) without stopping a
// publish.

const authoring = {
    verifyProject: vi.fn(),
    getNextVersionNumber: vi.fn(),
    publishProject: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: PublishProjectWizardModal } = await import('./PublishProjectWizardModal.vue');

async function verifyStep() {
    const wrapper = mount(PublishProjectWizardModal, {
        props: { projectSlug: 'p' },
        global: { stubs: { FontAwesomeIcon: true, teleport: true } },
    });
    await wrapper.findAll('button').find((b) => b.text().includes('Next')).trigger('click');
    await flushPromises();
    return wrapper;
}

beforeEach(() => vi.clearAllMocks());

describe('PublishProjectWizardModal warnings (#397)', () => {
    it('lists the warnings of a valid project', async () => {
        authoring.verifyProject.mockResolvedValue({
            valid: true,
            warnings: { 'intro/welcome': ['Node "Next" has speaker "Robni", which isn\'t one of the project\'s speakers'] },
        });
        const wrapper = await verifyStep();

        expect(wrapper.text()).toContain('No errors found');
        expect(wrapper.get('[data-test="verify-warnings"]').text()).toContain('1 warning.');
        expect(wrapper.get('[data-test="verify-warning-dialogue"]').text()).toContain('intro/welcome');
        expect(wrapper.text()).toContain('"Robni"');
    });

    it('shows no warnings when there are none', async () => {
        authoring.verifyProject.mockResolvedValue({ valid: true, warnings: {} });
        const wrapper = await verifyStep();
        expect(wrapper.find('[data-test="verify-warnings"]').exists()).toBe(false);
    });
});
