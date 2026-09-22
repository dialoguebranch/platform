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

// Covers the #256 follow-up to #252: DialogueBranchAuthoringClient's draft-test methods now
// treat timeZone the same way DialogueBranchClient's methods do — caller-supplied, sent only
// when given, no fallback to a client-computed default.

import { describe, it, expect, vi, afterEach } from 'vitest';
import { DialogueBranchAuthoringClient } from './DialogueBranchAuthoringClient.js';
import { DialogueStep } from './model/DialogueStep.js';

function jsonResponse(payload) {
    return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'content-type': 'application/json' },
    });
}

function okResponse() {
    return new Response(null, { status: 200 });
}

const START_RESPONSE = {
    draftSessionId: 'draft-1',
    dialogueMessage: {
        dialogue: 'inputs', node: 'Start', speaker: 'Martin McOwl',
        loggedDialogueId: 'ld-1', loggedInteractionIndex: 0,
        statement: { segments: [{ segmentType: 'TEXT', text: 'hi' }] },
        replies: [],
    },
};

function client() {
    return new DialogueBranchAuthoringClient({ baseUrl: '/api/v1' });
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('DialogueBranchAuthoringClient draft-test methods: timeZone', () => {
    it('startDraftDialogue() omits timeZone when not given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(START_RESPONSE));
        vi.stubGlobal('fetch', fetchMock);

        const { dialogueStep } = await client().startDraftDialogue({
            projectSlug: 'default-test', dialogueName: 'inputs', language: 'en',
        });

        const url = fetchMock.mock.calls[0][0];
        expect(url).not.toContain('timeZone');
        expect(dialogueStep).toBeInstanceOf(DialogueStep);
    });

    it('startDraftDialogue() includes timeZone when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(START_RESPONSE));
        vi.stubGlobal('fetch', fetchMock);

        await client().startDraftDialogue({
            projectSlug: 'default-test', dialogueName: 'inputs', language: 'en',
            timeZone: 'Europe/Lisbon',
        });

        expect(fetchMock.mock.calls[0][0]).toContain('timeZone=Europe%2FLisbon');
    });

    it('progressDraftDialogue() omits timeZone when not given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: null }));
        vi.stubGlobal('fetch', fetchMock);

        await client().progressDraftDialogue({ draftSessionId: 'draft-1', replyId: 1 });

        expect(fetchMock.mock.calls[0][0]).not.toContain('timeZone');
    });

    it('progressDraftDialogue() includes timeZone when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: null }));
        vi.stubGlobal('fetch', fetchMock);

        await client().progressDraftDialogue({ draftSessionId: 'draft-1', replyId: 1, timeZone: 'Europe/Lisbon' });

        expect(fetchMock.mock.calls[0][0]).toContain('timeZone=Europe%2FLisbon');
    });

    it('revertDraftVariables() omits timeZone when not given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().revertDraftVariables({ draftSessionId: 'draft-1' });

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).not.toContain('timeZone');
        expect(options.method).toBe('POST');
    });

    it('revertDraftVariables() includes timeZone when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().revertDraftVariables({ draftSessionId: 'draft-1', timeZone: 'Europe/Lisbon' });

        expect(fetchMock.mock.calls[0][0]).toContain('timeZone=Europe%2FLisbon');
    });
});
