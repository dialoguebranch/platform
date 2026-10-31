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

// "Test as" a female, male or undefined user (#454): shows and sets $dlb.userGender in the
// tester's copy of the variables.

const authoring = {
    getTestVariables: vi.fn(),
    setTestVariable: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
    getBrowserTimeZone: () => 'UTC',
}));

const { default: TestUserGenderSelect } = await import('./TestUserGenderSelect.vue');

async function mountSelect() {
    const wrapper = mount(TestUserGenderSelect, {
        global: { provide: { state: ref({ selectedProject: { slug: 'p' } }) } },
    });
    await flushPromises();
    return wrapper;
}

const select = (wrapper) => wrapper.get('[data-test="test-user-gender"]');

beforeEach(() => {
    vi.clearAllMocks();
    authoring.setTestVariable.mockResolvedValue(undefined);
});

describe('TestUserGenderSelect', () => {
    it('shows the test value of $dlb.userGender', async () => {
        authoring.getTestVariables.mockResolvedValue([{ name: 'dlb.userGender', value: 'female' }]);
        expect(select(await mountSelect()).element.value).toBe('female');
    });

    it('shows "any user" without a value', async () => {
        authoring.getTestVariables.mockResolvedValue([]);
        expect(select(await mountSelect()).element.value).toBe('undefined');
    });

    it('sets the test value, and clears it for any user', async () => {
        authoring.getTestVariables.mockResolvedValue([]);
        const wrapper = await mountSelect();
        await select(wrapper).setValue('male');
        await flushPromises();
        expect(authoring.setTestVariable).toHaveBeenLastCalledWith({
            projectSlug: 'p', variableName: 'dlb.userGender', variableValue: 'male', timeZone: 'UTC',
        });
        expect(wrapper.emitted('changed')).toHaveLength(1);

        await select(wrapper).setValue('undefined');
        await flushPromises();
        expect(authoring.setTestVariable.mock.calls.at(-1)[0].variableValue).toBeNull();
    });

    it('reloads when asked, after a change elsewhere', async () => {
        authoring.getTestVariables.mockResolvedValue([]);
        const wrapper = await mountSelect();
        authoring.getTestVariables.mockResolvedValue([{ name: 'dlb.userGender', value: 'male' }]);
        wrapper.vm.load();
        await flushPromises();
        expect(select(wrapper).element.value).toBe('male');
    });
});
