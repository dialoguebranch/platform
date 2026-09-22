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

import { describe, it, expect, vi, afterEach } from 'vitest';
import { DialogueBranchClient } from './DialogueBranchClient.js';
import { DialogueStep } from './model/DialogueStep.js';
import { ServerInfo } from './model/ServerInfo.js';
import { OngoingDialogue } from './model/OngoingDialogue.js';
import { Variable } from './model/Variable.js';

function jsonResponse(payload) {
    return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'content-type': 'application/json' },
    });
}

function okResponse() {
    return new Response(null, { status: 200 });
}

const STEP_JSON = {
    dialogue: 'inputs', node: 'Start', speaker: 'Martin McOwl',
    loggedDialogueId: 'ld-1', loggedInteractionIndex: 0,
    statement: { segments: [{ segmentType: 'TEXT', text: 'hi' }] },
    replies: [],
};

function client(options = {}) {
    return new DialogueBranchClient({ baseUrl: '/api/v1', ...options });
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('DialogueBranchClient playback methods', () => {
    it('logout() POSTs to /auth/logout', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().logout();

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/v1/auth/logout');
        expect(options.method).toBe('POST');
    });

    it('getServerInfo() GETs /info/all and parses a ServerInfo', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
            serviceVersion: '0.1.8', protocolVersion: '1', build: '2026-09-17', upTime: '1h',
        }));
        vi.stubGlobal('fetch', fetchMock);

        const info = await client().getServerInfo();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/info/all');
        expect(info).toBeInstanceOf(ServerInfo);
        expect(info.serviceVersion).toBe('0.1.8');
    });

    it('listDialogues() GETs with no query string when projectSlug is omitted', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ dialogueNames: ['menu'] }));
        vi.stubGlobal('fetch', fetchMock);

        const result = await client().listDialogues();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/dialogue/list-dialogues');
        expect(result).toEqual({ dialogueNames: ['menu'] });
    });

    it('listDialogues({ projectSlug }) GETs with the project slug encoded', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ dialogueNames: ['menu'] }));
        vi.stubGlobal('fetch', fetchMock);

        await client().listDialogues({ projectSlug: 'a project' });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/dialogue/list-dialogues?projectSlug=a%20project');
    });

    it('startDialogue() sends only dialogueName when projectSlug/language/timeZone are omitted', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STEP_JSON));
        vi.stubGlobal('fetch', fetchMock);

        const step = await client().startDialogue({ dialogueName: 'menu' });

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/v1/dialogue/start?dialogueName=menu');
        expect(options.method).toBe('POST');
        expect(step).toBeInstanceOf(DialogueStep);
        expect(step.node).toBe('Start');
    });

    it('startDialogue() includes projectSlug/language/timeZone/startNodeId when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STEP_JSON));
        vi.stubGlobal('fetch', fetchMock);

        await client().startDialogue({
            dialogueName: 'menu', projectSlug: 'default-test', language: 'en',
            timeZone: 'Europe/Lisbon', startNodeId: 'SomeNode',
        });

        const url = fetchMock.mock.calls[0][0];
        expect(url).toContain('dialogueName=menu');
        expect(url).toContain('projectSlug=default-test');
        expect(url).toContain('language=en');
        expect(url).toContain('timeZone=Europe%2FLisbon');
        expect(url).toContain('startNodeId=SomeNode');
    });

    it('startDialogue() appends &delegateUser= when delegateUser is set', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STEP_JSON));
        vi.stubGlobal('fetch', fetchMock);

        const c = client();
        c.delegateUser = 'bob';
        await c.startDialogue({ dialogueName: 'menu' });

        expect(fetchMock.mock.calls[0][0]).toContain('&delegateUser=bob');
    });

    it('progressDialogue() parses the next DialogueStep when the dialogue continues', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: STEP_JSON }));
        vi.stubGlobal('fetch', fetchMock);

        const step = await client().progressDialogue('ld-1', 0, 3);

        expect(step).toBeInstanceOf(DialogueStep);
    });

    it('progressDialogue() resolves to null when the dialogue ended', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: null }));
        vi.stubGlobal('fetch', fetchMock);

        const step = await client().progressDialogue('ld-1', 0, 3);

        expect(step).toBeNull();
    });

    it('continueDialogue() sends only dialogueName when projectSlug/timeZone are omitted', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: STEP_JSON }));
        vi.stubGlobal('fetch', fetchMock);

        const step = await client().continueDialogue({ dialogueName: 'menu' });

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/v1/dialogue/continue?dialogueName=menu');
        expect(options.method).toBe('POST');
        expect(step).toBeInstanceOf(DialogueStep);
    });

    it('continueDialogue() includes projectSlug/timeZone when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: STEP_JSON }));
        vi.stubGlobal('fetch', fetchMock);

        await client().continueDialogue({ dialogueName: 'menu', projectSlug: 'default-test', timeZone: 'Europe/Lisbon' });

        const url = fetchMock.mock.calls[0][0];
        expect(url).toContain('projectSlug=default-test');
        expect(url).toContain('timeZone=Europe%2FLisbon');
    });

    it('continueDialogue() resolves to null when there is no ongoing session', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: null }));
        vi.stubGlobal('fetch', fetchMock);

        const step = await client().continueDialogue({ dialogueName: 'menu' });

        expect(step).toBeNull();
    });

    it('cancelDialogue(loggedDialogueId) POSTs to /dialogue/cancel', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().cancelDialogue('ld-1');

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/v1/dialogue/cancel?loggedDialogueId=ld-1');
        expect(options.method).toBe('POST');
    });

    it('back(loggedDialogueId, loggedInteractionIndex) POSTs to /dialogue/back and parses a DialogueStep', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STEP_JSON));
        vi.stubGlobal('fetch', fetchMock);

        const step = await client().back('ld-1', 2);

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/v1/dialogue/back?loggedDialogueId=ld-1&loggedInteractionIndex=2');
        expect(options.method).toBe('POST');
        expect(step).toBeInstanceOf(DialogueStep);
    });

    it('getVariables() GETs with no query string when projectSlug/timeZone are omitted', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariables();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/get');
    });

    it('getVariables({ projectSlug, timeZone }) maps the response array to Variable instances', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([
            { name: 'gold', value: '10', updatedTime: 1, updatedTimeZone: 'UTC', updatedSource: 'DLB_SCRIPT' },
        ]));
        vi.stubGlobal('fetch', fetchMock);

        const variables = await client().getVariables({ projectSlug: 'default-test', timeZone: 'Europe/Lisbon' });

        const url = fetchMock.mock.calls[0][0];
        expect(url).toContain('projectSlug=default-test');
        expect(url).toContain('timeZone=Europe%2FLisbon');
        expect(variables).toHaveLength(1);
        expect(variables[0]).toBeInstanceOf(Variable);
        expect(variables[0].name).toBe('gold');
    });

    it('getVariables() defaults to an empty array when the body is null', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(null));
        vi.stubGlobal('fetch', fetchMock);

        const variables = await client().getVariables();

        expect(variables).toEqual([]);
    });

    it('getVariables() builds a valid URL (leading ?, not &) when only delegateUser is set', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        const c = client();
        c.delegateUser = 'bob';
        await c.getVariables();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/get?delegateUser=bob');
    });

    it('getOngoingDialogue() parses an OngoingDialogue when one exists', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
            value: { dialogueName: 'menu', loggedDialogueId: 'ld-1', secondsSinceLastEngagement: 42 },
        }));
        vi.stubGlobal('fetch', fetchMock);

        const ongoing = await client().getOngoingDialogue({ projectSlug: 'default-test' });

        expect(fetchMock.mock.calls[0][0]).toContain('projectSlug=default-test');
        expect(ongoing).toBeInstanceOf(OngoingDialogue);
        expect(ongoing.loggedDialogueId).toBe('ld-1');
    });

    it('getOngoingDialogue() resolves to null when there is none', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: null }));
        vi.stubGlobal('fetch', fetchMock);

        const ongoing = await client().getOngoingDialogue();

        expect(ongoing).toBeNull();
    });

    it('getOngoingDialogue() builds a valid URL (leading ?, not &) when only delegateUser is set', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: null }));
        vi.stubGlobal('fetch', fetchMock);

        const c = client();
        c.delegateUser = 'bob';
        await c.getOngoingDialogue();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/dialogue/get-ongoing?delegateUser=bob');
    });

    it('setVariable() sends only name when projectSlug/timeZone are omitted', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().setVariable({ variableName: 'gold', variableValue: '10' });

        const [url, options] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/v1/variables/set-single?name=gold&value=10');
        expect(options.method).toBe('POST');
    });

    it('setVariable() includes projectSlug/timeZone when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().setVariable({
            variableName: 'gold', variableValue: '10',
            projectSlug: 'default-test', timeZone: 'Europe/Lisbon',
        });

        const url = fetchMock.mock.calls[0][0];
        expect(url).toContain('projectSlug=default-test');
        expect(url).toContain('timeZone=Europe%2FLisbon');
    });

    it('setVariable() omits the value param when clearing (null)', async () => {
        const fetchMock = vi.fn().mockResolvedValue(okResponse());
        vi.stubGlobal('fetch', fetchMock);

        await client().setVariable({ variableName: 'gold', variableValue: null });

        expect(fetchMock.mock.calls[0][0]).not.toContain('&value=');
    });
});
