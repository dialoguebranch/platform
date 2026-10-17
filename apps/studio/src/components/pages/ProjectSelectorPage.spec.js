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

// The project selection page: deleting a project with its destructive options (#315), and the
// deleted projects, shown to admins in a separate section and deleted permanently from there
// (#403).

const authoring = {
    listProjects: vi.fn(),
    listDeletedProjects: vi.fn(),
    purgeDeletedProject: vi.fn(),
    deleteProject: vi.fn(),
};

vi.mock('../../composables/client.js', () => ({
    useAuthoringClient: () => authoring,
}));
vi.mock('../../composables/state-management.js', () => ({
    useStateManagement: () => ({ logout: vi.fn() }),
}));

const { default: ProjectSelectorPage } = await import('./ProjectSelectorPage.vue');

const PROJECTS = [{ slug: 'live', draftDisplayName: 'Live', latestVersion: { versionNumber: 1, displayName: 'Live' } }];
const DELETED = [
    { slug: 'old__deleted', displayName: 'Old', deletedAt: '2026-10-15T10:00:00Z', usersWithVariables: 3, dialogueLogs: 12 },
    { slug: 'empty__deleted', displayName: 'Empty', deletedAt: '2026-10-14T10:00:00Z', usersWithVariables: 0, dialogueLogs: 0 },
];

async function mountPage(roles = ['admin']) {
    const wrapper = mount(ProjectSelectorPage, {
        global: {
            provide: { state: ref({ user: { username: 'u', roles }, selectedProject: null }) },
            stubs: {
                FontAwesomeIcon: true,
                teleport: true,
                CreateProjectWizardModal: true,
                ImportProjectModal: true,
            },
        },
    });
    await flushPromises();
    return wrapper;
}

describe('ProjectSelectorPage deleted projects', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.listProjects.mockResolvedValue(PROJECTS);
        authoring.listDeletedProjects.mockResolvedValue(DELETED);
        authoring.purgeDeletedProject.mockResolvedValue();
        authoring.deleteProject.mockResolvedValue();
    });

    it('shows a collapsed section of deleted projects to admins', async () => {
        const wrapper = await mountPage();
        const toggle = wrapper.find('[data-test="deleted-projects-toggle"]');
        expect(toggle.text()).toContain('Deleted projects (2)');
        expect(wrapper.findAll('[data-test="deleted-project"]')).toHaveLength(0);
        await toggle.trigger('click');
        const rows = wrapper.findAll('[data-test="deleted-project"]');
        expect(rows).toHaveLength(2);
        expect(rows[0].text()).toContain('old__deleted');
        expect(rows[0].text()).toContain('3 users with variables, 12 dialogue logs');
        expect(rows[1].text()).toContain('no user data left');
    });

    it('does not show or load deleted projects for editors', async () => {
        const wrapper = await mountPage(['editor']);
        expect(authoring.listDeletedProjects).not.toHaveBeenCalled();
        expect(wrapper.find('[data-test="deleted-projects"]').exists()).toBe(false);
    });

    it('shows no section when there are no deleted projects', async () => {
        authoring.listDeletedProjects.mockResolvedValue([]);
        const wrapper = await mountPage();
        expect(wrapper.find('[data-test="deleted-projects"]').exists()).toBe(false);
    });

    it('deletes a deleted project permanently only after its slug is typed', async () => {
        const wrapper = await mountPage();
        await wrapper.find('[data-test="deleted-projects-toggle"]').trigger('click');
        await wrapper.find('[data-test="purge-project"]').trigger('click');
        const confirm = wrapper.find('[data-test="purge-confirm"]');
        expect(confirm.text()).toContain('all of its users\' variables, variable history and dialogue logs');
        expect(confirm.text()).toContain('3 users with variables, 12 dialogue logs');
        const button = () => wrapper.find('[data-test="purge-confirm-button"]');
        expect(button().attributes('disabled')).toBeDefined();

        await wrapper.find('[data-test="purge-confirm-input"]').setValue('old');
        expect(button().attributes('disabled')).toBeDefined();
        await wrapper.find('[data-test="purge-confirm-input"]').setValue('old__deleted');
        expect(button().attributes('disabled')).toBeUndefined();
        await button().trigger('click');
        await flushPromises();

        expect(authoring.purgeDeletedProject).toHaveBeenCalledWith('old__deleted');
        expect(wrapper.find('[data-test="purge-confirm"]').exists()).toBe(false);
        expect(wrapper.findAll('[data-test="deleted-project"]').map((r) => r.text()))
            .toEqual([expect.stringContaining('empty__deleted')]);
    });

    it('keeps the project listed when deleting it permanently fails', async () => {
        authoring.purgeDeletedProject.mockRejectedValue({ status: 500 });
        const wrapper = await mountPage();
        await wrapper.find('[data-test="deleted-projects-toggle"]').trigger('click');
        await wrapper.find('[data-test="purge-project"]').trigger('click');
        await wrapper.find('[data-test="purge-confirm-input"]').setValue('old__deleted');
        await wrapper.find('[data-test="purge-confirm-button"]').trigger('click');
        await flushPromises();
        expect(wrapper.find('[data-test="purge-confirm"]').text()).toContain('Failed to delete the project permanently');
        expect(wrapper.findAll('[data-test="deleted-project"]')).toHaveLength(2);
    });
});

describe('ProjectSelectorPage delete project', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.listProjects.mockResolvedValue(PROJECTS);
        authoring.listDeletedProjects.mockResolvedValue([]);
        authoring.deleteProject.mockResolvedValue();
    });

    async function openDelete(wrapper) {
        await wrapper.find('[title="Delete project"]').trigger('click');
        await wrapper.find('input[placeholder="live"]').setValue('live');
    }

    it('keeps users\' data by default, and refreshes the deleted projects', async () => {
        const wrapper = await mountPage();
        await openDelete(wrapper);
        expect(wrapper.find('[data-test="delete-keeps-user-data"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="delete-variables"]').element.checked).toBe(false);
        expect(wrapper.find('[data-test="delete-logs"]').element.checked).toBe(false);
        await wrapper.findAll('button').find((b) => b.text() === 'Delete Project').trigger('click');
        await flushPromises();
        expect(authoring.deleteProject).toHaveBeenCalledWith('live', { deleteVariables: false, deleteLogs: false });
        expect(authoring.listDeletedProjects).toHaveBeenCalledTimes(2);
    });

    it('passes the destructive options when ticked', async () => {
        const wrapper = await mountPage();
        await openDelete(wrapper);
        await wrapper.find('[data-test="delete-variables"]').setValue(true);
        await wrapper.find('[data-test="delete-logs"]').setValue(true);
        await wrapper.findAll('button').find((b) => b.text() === 'Delete Project').trigger('click');
        await flushPromises();
        expect(authoring.deleteProject).toHaveBeenCalledWith('live', { deleteVariables: true, deleteLogs: true });
    });
});

describe('ProjectSelectorPage management menu', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.listProjects.mockResolvedValue(PROJECTS);
        authoring.listDeletedProjects.mockResolvedValue([]);
    });

    it('opens User Management for admins', async () => {
        const wrapper = await mountPage(['admin']);
        await wrapper.find('[data-test="menu-users"]').trigger('click');
        expect(wrapper.vm.$.provides.state.value.adminPage).toBe('users');
    });

    it('offers User Management to admins only', async () => {
        const wrapper = await mountPage(['editor']);
        expect(wrapper.find('[data-test="menu-users"]').exists()).toBe(false);
    });
});
