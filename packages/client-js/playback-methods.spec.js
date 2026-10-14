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
import { VariableHistoryEntry } from './model/VariableHistoryEntry.js';

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

    it('getVariableHistory() GETs with no query string when every option is omitted', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariableHistory();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history');
        expect(fetchMock.mock.calls[0][1].method).toBe('GET');
    });

    it('getVariableHistory() sends projectSlug/variableName/page/pageSize when given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariableHistory({ projectSlug: 'default-test', variableName: 'mood',
            page: 2, pageSize: 25 });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history'
            + '?projectSlug=default-test&variableName=mood&page=2&pageSize=25');
    });

    it('getVariableHistory() sends from and to as epoch milliseconds, between variableName and page', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariableHistory({ projectSlug: 'p', variableName: 'mood',
            from: 1000, to: 2000, page: 1, pageSize: 10 });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history'
            + '?projectSlug=p&variableName=mood&from=1000&to=2000&page=1&pageSize=10');
    });

    it('getVariableHistory() accepts Date objects for from and to', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariableHistory({ from: new Date(1000), to: new Date(2000) });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history?from=1000&to=2000');
    });

    it('getVariableHistory() sends from=0 and either bound on its own', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse([])));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariableHistory({ from: 0 });
        await client().getVariableHistory({ to: 5 });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history?from=0');
        expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/variables/history?to=5');
    });

    it('getVariableHistory() sends page=0 (a falsy but meaningful value)', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        await client().getVariableHistory({ projectSlug: 'p', page: 0 });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history?projectSlug=p&page=0');
    });

    it('getVariableHistory() maps the response array to VariableHistoryEntry instances, keeping order and nulls', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse([
            { id: 12, name: 'mood', value: null, recordedTime: 3, recordedTimeZone: 'Europe/Lisbon', source: 'WEB_SERVICE' },
            { id: 11, name: 'mood', value: 'fine', recordedTime: 2, recordedTimeZone: 'Europe/Lisbon', source: 'DLB_SCRIPT' },
        ]));
        vi.stubGlobal('fetch', fetchMock);

        const entries = await client().getVariableHistory({ projectSlug: 'default-test' });

        expect(entries).toHaveLength(2);
        expect(entries[0]).toBeInstanceOf(VariableHistoryEntry);
        expect(entries.map((e) => e.id)).toEqual([12, 11]);
        expect(entries[0].value).toBeNull();
        expect(entries[0].isRemoval).toBe(true);
        expect(entries[1].value).toBe('fine');
        expect(entries[1].source).toBe('DLB_SCRIPT');
    });

    it('getVariableHistory() defaults to an empty array when the body is null', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(null)));

        expect(await client().getVariableHistory()).toEqual([]);
    });

    it('getVariableHistory() appends delegateUser, with a leading ? when it is the only param', async () => {
        // Two calls: each needs its own Response, since a body can only be read once.
        const fetchMock = vi.fn().mockImplementation(async () => jsonResponse([]));
        vi.stubGlobal('fetch', fetchMock);

        const c = client();
        c.delegateUser = 'bob';
        await c.getVariableHistory();
        await c.getVariableHistory({ projectSlug: 'p' });

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/variables/history?delegateUser=bob');
        expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/variables/history?projectSlug=p&delegateUser=bob');
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
