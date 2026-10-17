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

// The delegate user picker: it lists users found by username or subject, including users with
// no username, who are picked and shown by their subject (#407).

const authoring = { listUsers: vi.fn() };

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));

const { default: SetDelegateUserModal } = await import('./SetDelegateUserModal.vue');

describe('SetDelegateUserModal', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.listUsers.mockResolvedValue([
            { username: 'robin', subject: 's-robin', variables: 0, dialogueLogs: 0 },
            { username: null, subject: 's-delegate', variables: 0, dialogueLogs: 0 },
        ]);
    });

    it('picks a user with no username by subject', async () => {
        const wrapper = mount(SetDelegateUserModal, {
            global: { stubs: { FontAwesomeIcon: true, teleport: true } },
        });
        await flushPromises();
        expect(authoring.listUsers).toHaveBeenCalledWith({ search: '', page: 0, pageSize: 50 });

        const rows = wrapper.findAll('button').filter((b) => b.text().includes('s-delegate'));
        expect(rows[0].text()).toContain('no username');
        await rows[0].trigger('click');
        await wrapper.find('[data-test="simulate-as"]').trigger('click');

        expect(wrapper.emitted('apply')[0][0]).toEqual({ subject: 's-delegate', label: 's-delegate', mode: 'simulate' });
    });

    // #411: simulating is the primary choice, acting for real the secondary one.
    it('offers to simulate or to act as the picked user', async () => {
        const wrapper = mount(SetDelegateUserModal, {
            global: { stubs: { FontAwesomeIcon: true, teleport: true } },
        });
        await flushPromises();
        expect(wrapper.find('[data-test="act-as"]').attributes('disabled')).toBeDefined();

        await wrapper.findAll('button').find((b) => b.text().includes('robin')).trigger('click');
        await wrapper.find('[data-test="act-as"]').trigger('click');
        await wrapper.find('[data-test="simulate-as"]').trigger('click');

        expect(wrapper.emitted('apply').map((e) => e[0].mode)).toEqual(['act', 'simulate']);
        expect(wrapper.emitted('apply')[0][0]).toEqual({ subject: 's-robin', label: 'robin', mode: 'act' });
    });
});
