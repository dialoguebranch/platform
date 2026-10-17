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

// The participant view starts the dialogue set up for the participant role when Studio is built.
// When it doesn't exist, it says so plainly, without a pointless "Try Again".

const playback = { startDialogue: vi.fn(), progressDialogue: vi.fn() };

vi.mock('../../composables/client.js', () => ({
    useClient: () => playback,
    getBrowserTimeZone: () => 'UTC',
}));
vi.mock('../../composables/state-management.js', () => ({
    useStateManagement: () => ({ logout: vi.fn() }),
}));

const { default: ParticipantPage } = await import('./ParticipantPage.vue');

function mountPage() {
    return mount(ParticipantPage, {
        global: {
            provide: {
                state: ref({ user: { name: 'p', roles: ['participant'] } }),
                config: { participant: { projectSlug: 'some-project', dialogueName: 'some-dialogue' } },
            },
            stubs: { FontAwesomeIcon: true, CollapsibleErrorList: true },
        },
    });
}

const tryAgain = (wrapper) => wrapper.findAll('button').find((b) => b.text() === 'Try Again');

describe('ParticipantPage', () => {
    beforeEach(() => vi.clearAllMocks());

    it('says no test dialogue is set up when it does not exist, without naming it or offering to retry', async () => {
        playback.startDialogue.mockRejectedValue(Object.assign(new Error("Dialogue 'some-dialogue' not found in project 'some-project'."), { status: 404 }));
        const wrapper = mountPage();
        await flushPromises();

        expect(wrapper.text()).toContain('There is no test dialogue set up to run for your user role (participant).');
        expect(wrapper.text()).not.toContain('some-dialogue');
        expect(wrapper.text()).not.toContain('some-project');
        expect(tryAgain(wrapper)).toBeUndefined();
    });

    it('offers to try again after another error', async () => {
        playback.startDialogue.mockRejectedValue(Object.assign(new Error('Service unavailable'), { status: 503 }));
        const wrapper = mountPage();
        await flushPromises();

        expect(wrapper.text()).toContain('Service unavailable');
        await tryAgain(wrapper).trigger('click');
        expect(playback.startDialogue).toHaveBeenCalledTimes(2);
    });
});
