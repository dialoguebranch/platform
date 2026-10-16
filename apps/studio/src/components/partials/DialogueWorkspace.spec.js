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
import { DLB_APP_MODE_DRAFT, DIALOGUE_WORKSPACE_STYLE_BALLOONS } from '@/StudioClientState.js';

// How the Dialogue Workspace handles the end of a test session (#218): the Web Service ends a
// session with its dialogue and evicts idle ones, answering 404 for them afterwards.

const playback = {};
const authoring = {
    getProject: vi.fn(),
    startTestSession: vi.fn(),
    progressTestSession: vi.fn(),
    cancelTestSession: vi.fn(),
};

vi.mock('@/composables/client.js', () => ({
    useClient: () => playback,
    useAuthoringClient: () => authoring,
    getBrowserTimeZone: () => 'UTC',
}));

globalThis.ResizeObserver ??= class { observe() {} disconnect() {} };

const { default: DialogueWorkspace } = await import('./DialogueWorkspace.vue');

const reply = { replyId: 1, statement: { segments: [{ text: 'Next' }] } };
const step = (replies) => ({ dialogueName: 'main', node: 'Start', statement: { segments: [] }, replies, actions: [] });

async function mountWorkspace() {
    const state = ref({
        selectedProject: { slug: 'p' },
        mode: DLB_APP_MODE_DRAFT,
        dialogueWorkspaceStyle: DIALOGUE_WORKSPACE_STYLE_BALLOONS,
    });
    const wrapper = mount(DialogueWorkspace, {
        global: {
            provide: { state },
            stubs: {
                FontAwesomeIcon: true,
                BalloonDialogueComponent: true,
                TextDialogueComponent: true,
                DialogueEditor: true,
                TranslationEditor: true,
                teleport: true,
            },
        },
    });
    await flushPromises();
    wrapper.vm.loadDraftDialogue('main');
    await flushPromises();
    return wrapper;
}

const balloons = (wrapper) => wrapper.findComponent({ name: 'BalloonDialogueComponent' });

describe('DialogueWorkspace — test sessions', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.getProject.mockResolvedValue({ sourceLanguageCode: 'en', draftTranslationLanguages: [] });
        authoring.startTestSession.mockResolvedValue({ testSessionId: 's1', dialogueStep: step([reply]) });
        authoring.cancelTestSession.mockResolvedValue();
    });

    it('shows that an expired test session can be started again', async () => {
        authoring.progressTestSession.mockRejectedValue(Object.assign(new Error('Test session not found'), { status: 404 }));
        const wrapper = await mountWorkspace();

        balloons(wrapper).vm.$emit('selectReply', step([reply]), reply);
        await flushPromises();

        expect(balloons(wrapper).props('startError').message).toContain('expired');
        balloons(wrapper).vm.$emit('restartDialogue');
        await flushPromises();
        expect(authoring.startTestSession).toHaveBeenCalledTimes(2);
        expect(balloons(wrapper).props('startError')).toBe(null);
    });

    it('forgets a session once its dialogue has ended', async () => {
        authoring.progressTestSession.mockResolvedValue(step([]));
        const wrapper = await mountWorkspace();
        expect(wrapper.text()).toContain('Session ID: s1');

        balloons(wrapper).vm.$emit('selectReply', step([reply]), reply);
        await flushPromises();

        expect(balloons(wrapper).props('dialogueEnded')).toBe(true);
        // The Web Service has ended it too, so nothing refers to it any more.
        expect(wrapper.text()).not.toContain('Session ID: s1');
    });
});
