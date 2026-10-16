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

// The Users page (#407): lists the users with their data, searches them, and erases one after
// typing their name back.

const authoring = {
    listUsers: vi.fn(),
    eraseUser: vi.fn(),
};

vi.mock('../../composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));
vi.mock('../../composables/state-management.js', () => ({
    useStateManagement: () => ({ logout: vi.fn() }),
}));

const { default: UsersPage } = await import('./UsersPage.vue');

const USERS = [
    { username: 'robin', subject: 's-robin', variables: 4, dialogueLogs: 1 },
    { username: null, subject: 's-delegate', variables: 1, dialogueLogs: 0 },
    { username: 'idle', subject: 's-idle', variables: 0, dialogueLogs: 0 },
];

async function mountPage() {
    const state = ref({ user: { username: 'u', roles: ['admin'] }, adminPage: 'users' });
    const wrapper = mount(UsersPage, {
        global: {
            provide: { state },
            stubs: { FontAwesomeIcon: true, teleport: true },
        },
    });
    await flushPromises();
    return { wrapper, state };
}

async function openErase(wrapper, index) {
    await wrapper.findAll('[data-test="erase-user"]')[index].trigger('click');
    // Found afresh each time: the confirmation re-renders as it's typed into.
    return {
        input: () => wrapper.find('[data-test="erase-confirm-input"]'),
        button: () => wrapper.find('[data-test="erase-confirm-button"]'),
    };
}

describe('UsersPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.useRealTimers();
        authoring.listUsers.mockResolvedValue(USERS);
        authoring.eraseUser.mockResolvedValue({ variables: 4, variableHistory: 9, dialogueLogs: 1 });
    });

    it('lists the users with their data', async () => {
        const { wrapper } = await mountPage();
        expect(authoring.listUsers).toHaveBeenCalledWith({ search: '', page: 0, pageSize: 50 });
        const rows = wrapper.findAll('[data-test="user-row"]');
        expect(rows).toHaveLength(3);
        expect(rows[0].text()).toContain('robin');
        expect(rows[0].find('[data-test="user-data"]').text()).toBe('4 variables · 1 dialogue log');
        expect(rows[1].text()).toContain('No username');
        expect(rows[2].find('[data-test="user-data"]').text()).toBe('No data');
    });

    it('searches once typing pauses', async () => {
        vi.useFakeTimers();
        const { wrapper } = await mountPage();
        await wrapper.find('[data-test="user-search"]').setValue('rob');
        expect(authoring.listUsers).toHaveBeenCalledTimes(1);
        vi.advanceTimersByTime(300);
        await flushPromises();
        expect(authoring.listUsers).toHaveBeenLastCalledWith({ search: 'rob', page: 0, pageSize: 50 });
    });

    it('loads the next page when there may be more', async () => {
        const page = Array.from({ length: 50 }, (_, i) => ({ username: `u${i}`, subject: `s${i}`, variables: 0, dialogueLogs: 0 }));
        authoring.listUsers.mockResolvedValueOnce(page).mockResolvedValueOnce([USERS[0]]);
        const { wrapper } = await mountPage();
        await wrapper.find('[data-test="load-more"]').trigger('click');
        await flushPromises();
        expect(authoring.listUsers).toHaveBeenLastCalledWith({ search: '', page: 1, pageSize: 50 });
        expect(wrapper.findAll('[data-test="user-row"]')).toHaveLength(51);
        expect(wrapper.find('[data-test="load-more"]').exists()).toBe(false);
    });

    it('erases a user only after typing their username', async () => {
        const { wrapper } = await mountPage();
        const confirm = await openErase(wrapper, 0);
        expect(confirm.button().attributes('disabled')).toBeDefined();
        await confirm.input().setValue('robi');
        expect(confirm.button().attributes('disabled')).toBeDefined();
        await confirm.input().setValue('robin');
        await confirm.button().trigger('click');
        await flushPromises();

        expect(authoring.eraseUser).toHaveBeenCalledWith('s-robin');
        expect(wrapper.find('[data-test="erase-confirm"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="erased-notice"]').text())
            .toContain('4 variables, 9 history entries, 1 dialogue log');
        expect(wrapper.findAll('[data-test="user-row"]').map((r) => r.text()).join()).not.toContain('robin');
    });

    it('asks for the subject of a user with no username', async () => {
        const { wrapper } = await mountPage();
        const confirm = await openErase(wrapper, 1);
        await confirm.input().setValue('s-delegate');
        await confirm.button().trigger('click');
        await flushPromises();
        expect(authoring.eraseUser).toHaveBeenCalledWith('s-delegate');
    });

    it('treats an already erased user as erased', async () => {
        authoring.eraseUser.mockRejectedValue({ status: 404 });
        const { wrapper } = await mountPage();
        const confirm = await openErase(wrapper, 2);
        await confirm.input().setValue('idle');
        await confirm.button().trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="erased-notice"]').exists()).toBe(true);
        expect(wrapper.findAll('[data-test="user-row"]')).toHaveLength(2);
    });

    it('keeps the confirmation open when erasing fails', async () => {
        authoring.eraseUser.mockRejectedValue({ status: 500 });
        const { wrapper } = await mountPage();
        const confirm = await openErase(wrapper, 0);
        await confirm.input().setValue('robin');
        await confirm.button().trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="erase-confirm"]').text()).toContain('Failed to erase');
        expect(wrapper.findAll('[data-test="user-row"]')).toHaveLength(3);
    });

    it('goes back to the project selection', async () => {
        const { wrapper, state } = await mountPage();
        await wrapper.find('[data-test="back-to-projects"]').trigger('click');
        expect(state.value.adminPage).toBe(null);
    });
});
