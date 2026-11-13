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
import { DLB_APP_MODE_DRAFT, DLB_APP_MODE_LIVE, DIALOGUE_WORKSPACE_STYLE_BALLOONS } from '@/StudioClientState.js';

// How the Dialogue Workspace handles the end of a test session (#218): the Web Service ends a
// session with its dialogue and evicts idle ones, answering 404 for them afterwards.

const playback = {
    startDialogue: vi.fn(),
    progressDialogue: vi.fn(),
    continueDialogue: vi.fn(),
    cancelDialogue: vi.fn(),
    getOngoingDialogue: vi.fn(),
    setVariable: vi.fn(),
    back: vi.fn(),
};
const authoring = {
    getProject: vi.fn(),
    startTestSession: vi.fn(),
    progressTestSession: vi.fn(),
    cancelTestSession: vi.fn(),
    backTestSession: vi.fn(),
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

// #411: while simulating a user, every dialogue runs as a test session — on the published content
// in Live Mode — and nothing goes through the playback client's real dialogue end-points.
describe('DialogueWorkspace — simulating a user', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.getProject.mockResolvedValue({ sourceLanguageCode: 'en', draftTranslationLanguages: [] });
        authoring.startTestSession.mockResolvedValue({ testSessionId: 's1', dialogueStep: step([reply]) });
        authoring.progressTestSession.mockResolvedValue(step([reply]));
        authoring.cancelTestSession.mockResolvedValue();
    });

    async function mountSimulatingIn(mode) {
        const state = ref({
            selectedProject: { slug: 'p' },
            mode,
            dialogueWorkspaceStyle: DIALOGUE_WORKSPACE_STYLE_BALLOONS,
            delegate: { subject: 's-robin', label: 'robin', mode: 'simulate' },
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
        return wrapper;
    }

    const noRealPlayback = () => {
        for (const method of Object.values(playback)) expect(method).not.toHaveBeenCalled();
    };

    it('runs the published content as a test session in Live Mode', async () => {
        const wrapper = await mountSimulatingIn(DLB_APP_MODE_LIVE);

        wrapper.vm.openDialogue('main');
        await flushPromises();
        balloons(wrapper).vm.$emit('selectReply', step([reply]), reply);
        await flushPromises();
        await wrapper.find('[title="Cancel the current dialogue"]').trigger('click');
        await flushPromises();

        expect(authoring.startTestSession).toHaveBeenCalledWith(expect.objectContaining({ dialogueName: 'main', content: 'published' }));
        expect(authoring.progressTestSession).toHaveBeenCalled();
        expect(authoring.cancelTestSession).toHaveBeenCalledWith('s1');
        expect(wrapper.text()).toContain('Simulation (published content)');
        noRealPlayback();
    });

    it('starts afresh instead of resuming the user\'s ongoing dialogue', async () => {
        const wrapper = await mountSimulatingIn(DLB_APP_MODE_LIVE);

        wrapper.vm.resumeDialogue('main');
        await flushPromises();

        expect(authoring.startTestSession).toHaveBeenCalledWith(expect.objectContaining({ content: 'published' }));
        noRealPlayback();
    });

    it('runs the draft content in Authoring Mode', async () => {
        const wrapper = await mountSimulatingIn(DLB_APP_MODE_DRAFT);

        wrapper.vm.openDialogue('main');
        await flushPromises();

        expect(authoring.startTestSession).toHaveBeenCalledWith(expect.objectContaining({ content: 'draft' }));
        noRealPlayback();
    });
});

// #473: a reply that leads into another dialogue gives a new log on the Web Service, and the
// workspace shows and uses that log from then on.
describe('DialogueWorkspace — a reply into another dialogue', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authoring.getProject.mockResolvedValue({ sourceLanguageCode: 'en', draftTranslationLanguages: [] });
        playback.getOngoingDialogue.mockResolvedValue(null);
        playback.startDialogue.mockResolvedValue({ ...step([reply]), loggedDialogueId: 'log-main', loggedInteractionIndex: 0 });
        playback.progressDialogue.mockResolvedValue({ ...step([reply]), dialogueName: 'other', loggedDialogueId: 'log-other', loggedInteractionIndex: 0 });
    });

    it('shows the new log of the dialogue it led into', async () => {
        const state = ref({
            selectedProject: { slug: 'p' },
            mode: DLB_APP_MODE_LIVE,
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
        wrapper.vm.openDialogue('main');
        await flushPromises();
        expect(wrapper.text()).toContain('Logged Dialogue ID: log-main');

        balloons(wrapper).vm.$emit('selectReply', { ...step([reply]), loggedDialogueId: 'log-main', loggedInteractionIndex: 0 }, reply);
        await flushPromises();

        expect(playback.progressDialogue).toHaveBeenCalledWith('log-main', 0, 1, null);
        expect(wrapper.text()).toContain('Logged Dialogue ID: log-other');
        expect(wrapper.text()).not.toContain('log-main');
    });
});

describe('DialogueWorkspace — resuming a dialogue (#489)', () => {
    async function mountLive() {
        const state = ref({
            selectedProject: { slug: 'p' },
            mode: DLB_APP_MODE_LIVE,
            dialogueWorkspaceStyle: DIALOGUE_WORKSPACE_STYLE_BALLOONS,
        });
        const wrapper = mount(DialogueWorkspace, {
            global: {
                provide: { state },
                stubs: {
                    FontAwesomeIcon: true, BalloonDialogueComponent: true, TextDialogueComponent: true,
                    DialogueEditor: true, TranslationEditor: true, teleport: true,
                },
            },
        });
        await flushPromises();
        return wrapper;
    }

    const languageSelector = (wrapper) => wrapper.find('select[title="Language new dialogue tests are started in"]');

    beforeEach(() => {
        vi.clearAllMocks();
        authoring.getProject.mockResolvedValue({
            sourceLanguageCode: 'en', sourceLanguageName: 'English',
            latestVersion: { publishedTranslationLanguages: [
                { translationLanguageCode: 'nl-NL', translationLanguageName: 'Dutch (Netherlands)' },
            ] },
        });
        playback.continueDialogue.mockResolvedValue({ ...step([reply]), loggedDialogueId: 'ld-1' });
    });

    it('shows the language the resumed dialogue runs in, without restarting it', async () => {
        const wrapper = await mountLive();
        expect(languageSelector(wrapper).element.value).toBe('en');

        wrapper.vm.resumeDialogue('basic', { language: 'nl-NL' });
        await flushPromises();

        expect(languageSelector(wrapper).element.value).toBe('nl-NL');
        expect(playback.continueDialogue).toHaveBeenCalledTimes(1);
        expect(playback.startDialogue).not.toHaveBeenCalled();
    });

    it('leaves the selector alone when the language isn\'t known or isn\'t offered', async () => {
        const wrapper = await mountLive();
        wrapper.vm.resumeDialogue('basic');
        await flushPromises();
        expect(languageSelector(wrapper).element.value).toBe('en');

        wrapper.vm.resumeDialogue('basic', { language: 'fr' });
        await flushPromises();
        expect(languageSelector(wrapper).element.value).toBe('en');
        expect(playback.startDialogue).not.toHaveBeenCalled();
    });
});

describe('DialogueWorkspace — going back a step (#490)', () => {
    async function mountLive() {
        const state = ref({
            selectedProject: { slug: 'p' },
            mode: DLB_APP_MODE_LIVE,
            dialogueWorkspaceStyle: DIALOGUE_WORKSPACE_STYLE_BALLOONS,
        });
        const wrapper = mount(DialogueWorkspace, {
            global: {
                provide: { state },
                stubs: {
                    FontAwesomeIcon: true, BalloonDialogueComponent: true, TextDialogueComponent: true,
                    DialogueEditor: true, TranslationEditor: true, teleport: true,
                },
            },
        });
        await flushPromises();
        return wrapper;
    }

    const at = (node, index, replies = [reply], log = 'ld-1') =>
        ({ ...step(replies), node, loggedDialogueId: log, loggedInteractionIndex: index });
    const backButton = (wrapper) => wrapper.find('button[data-test="dialogue-back"]');
    const shownSteps = (wrapper) => balloons(wrapper).props('dialogueSteps');

    beforeEach(() => {
        vi.clearAllMocks();
        authoring.getProject.mockResolvedValue({ sourceLanguageCode: 'en', sourceLanguageName: 'English' });
        playback.startDialogue.mockResolvedValue(at('Start', 0));
    });

    async function startAndReply(wrapper, next) {
        wrapper.vm.loadDialogue('main');
        await flushPromises();
        playback.progressDialogue.mockResolvedValue(next);
        balloons(wrapper).vm.$emit('selectReply', shownSteps(wrapper).at(-1), reply);
        await flushPromises();
    }

    it('goes back to the previous step, whose replies can be chosen again', async () => {
        const wrapper = await mountLive();
        await startAndReply(wrapper, at('Second', 2));
        const before = shownSteps(wrapper)[0];
        const again = at('Start', 0);
        playback.back.mockResolvedValue(again);

        await backButton(wrapper).trigger('click');
        await flushPromises();

        expect(playback.back).toHaveBeenCalledWith('ld-1', 2);
        const steps = shownSteps(wrapper);
        expect(steps).toHaveLength(1);
        expect(steps[0]).toStrictEqual(again);
        expect(steps[0]).not.toBe(before);
        expect(wrapper.emitted('newDialogueStep')?.length).toBeGreaterThan(1);
    });

    it('is off with no earlier step, after the end, and right after a reply into another dialogue', async () => {
        const wrapper = await mountLive();
        expect(backButton(wrapper).attributes('disabled')).toBeDefined();

        wrapper.vm.loadDialogue('main');
        await flushPromises();
        expect(backButton(wrapper).attributes('disabled')).toBeDefined();

        await startAndReply(wrapper, at('Other', 0, [reply], 'ld-2'));
        expect(backButton(wrapper).attributes('disabled')).toBeDefined();

        await startAndReply(wrapper, at('Last', 2, []));
        expect(backButton(wrapper).attributes('disabled')).toBeDefined();
    });

    it('goes back in a resumed dialogue too, whose earlier steps are only in its log', async () => {
        const wrapper = await mountLive();
        playback.continueDialogue.mockResolvedValue(at('Fourth', 6));
        wrapper.vm.resumeDialogue('main');
        await flushPromises();
        const again = at('Third', 4);
        playback.back.mockResolvedValue(again);

        await backButton(wrapper).trigger('click');
        await flushPromises();

        expect(playback.back).toHaveBeenCalledWith('ld-1', 6);
        expect(shownSteps(wrapper)).toStrictEqual([again]);
    });

    it('goes back in a draft test too, through its test session', async () => {
        const first = { ...step([reply]), loggedDialogueId: 'mem-1', loggedInteractionIndex: 0 };
        authoring.startTestSession.mockResolvedValue({ testSessionId: 's1', dialogueStep: first });
        const wrapper = await mountWorkspace();
        expect(backButton(wrapper).attributes('disabled')).toBeDefined();

        authoring.progressTestSession.mockResolvedValue({ ...step([reply]), node: 'Two', loggedDialogueId: 'mem-1', loggedInteractionIndex: 2 });
        balloons(wrapper).vm.$emit('selectReply', shownSteps(wrapper).at(-1), reply);
        await flushPromises();
        const again = { ...first };
        authoring.backTestSession.mockResolvedValue(again);

        await backButton(wrapper).trigger('click');
        await flushPromises();

        expect(authoring.backTestSession).toHaveBeenCalledWith({ testSessionId: 's1', timeZone: 'UTC' });
        expect(playback.back).not.toHaveBeenCalled();
        expect(shownSteps(wrapper)).toStrictEqual([again]);
    });

    it('shows that a draft test expired when going back finds no session', async () => {
        authoring.startTestSession.mockResolvedValue({ testSessionId: 's1',
            dialogueStep: { ...step([reply]), loggedDialogueId: 'mem-1', loggedInteractionIndex: 0 } });
        const wrapper = await mountWorkspace();
        authoring.progressTestSession.mockResolvedValue({ ...step([reply]), loggedDialogueId: 'mem-1', loggedInteractionIndex: 2 });
        balloons(wrapper).vm.$emit('selectReply', shownSteps(wrapper).at(-1), reply);
        await flushPromises();
        authoring.backTestSession.mockRejectedValue({ status: 404, message: 'Test session not found' });

        await backButton(wrapper).trigger('click');
        await flushPromises();

        expect(balloons(wrapper).props('startError')).toBeTruthy();
    });
});

