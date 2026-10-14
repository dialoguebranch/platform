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

// Covers #350: draft node headers travel as an ordered list of { key, value } tags, not text.

import { describe, it, expect, vi } from 'vitest';
import { DialogueBranchAuthoringClient } from './DialogueBranchAuthoringClient.js';

const NODE = {
    id: 'n1',
    title: 'Start',
    tags: [{ key: 'title', value: 'Start' }, { key: '2', value: 'digit key' }],
    body: 'Hi.',
};

function clientReturning(payload) {
    const fetch = vi.fn(async () => new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'content-type': 'application/json' },
    }));
    return { client: new DialogueBranchAuthoringClient({ baseUrl: '/api/v1', fetch }), fetch };
}

describe('draft node methods', () => {
    it('createDraftNode sends the tags and body, and no separate title', async () => {
        const { client, fetch } = clientReturning(NODE);
        const node = await client.createDraftNode('proj', 'main', NODE.tags, 'Hi.');
        const [url, init] = fetch.mock.calls[0];
        expect(url).toBe('/api/v1/authoring/create-node?projectSlug=proj&dialogueName=main');
        expect(JSON.parse(init.body)).toEqual({ tags: NODE.tags, body: 'Hi.' });
        expect(node.tags).toEqual(NODE.tags);
    });

    it('updateDraftNode sends the complete tags and body for the node', async () => {
        const { client, fetch } = clientReturning(NODE);
        await client.updateDraftNode('proj', 'main', 'Start', NODE.tags, 'Hi.');
        const [url, init] = fetch.mock.calls[0];
        expect(url).toContain('/authoring/update-node?');
        expect(url).toContain('nodeTitle=Start');
        expect(JSON.parse(init.body)).toEqual({ tags: NODE.tags, body: 'Hi.' });
    });

    it('listDraftNodes keeps the order of tags as received', async () => {
        const { client } = clientReturning([NODE]);
        const nodes = await client.listDraftNodes('proj', 'main');
        expect(nodes[0].tags.map((tag) => tag.key)).toEqual(['title', '2']);
    });

    it('renameDraftNode returns the renamed node and the number of updated references', async () => {
        const { client } = clientReturning({ node: NODE, referencesUpdated: 2 });
        const result = await client.renameDraftNode('proj', 'main', 'Old', 'Start', true);
        expect(result.referencesUpdated).toBe(2);
        expect(result.node.tags[0]).toEqual({ key: 'title', value: 'Start' });
    });
});

describe('draft preamble', () => {
    it('updateDraftPreamble sends the preamble and returns it as stored', async () => {
        const { client, fetch } = clientReturning({ preamble: '// About.' });
        const result = await client.updateDraftPreamble('proj', 'folder/main', '// About.\n');
        const [url, init] = fetch.mock.calls[0];
        expect(url).toBe('/api/v1/authoring/update-preamble?projectSlug=proj&dialogueName=folder%2Fmain');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual({ preamble: '// About.\n' });
        expect(result.preamble).toBe('// About.');
    });
});

describe('importScripts', () => {
    it('sends the project, folder, replace flag and files as multipart form data', async () => {
        const { client, fetch } = clientReturning([
            { fileName: 'welcome.dlb', dialogueName: 'onboarding/welcome', replaced: false, parseErrors: [] },
        ]);
        const welcome = new File(['title: Start\n---\nHi.\n===\n'], 'welcome.dlb', { type: 'text/plain' });
        const results = await client.importScripts('proj', 'onboarding', [welcome], { replace: true });
        const [url, init] = fetch.mock.calls[0];
        expect(url).toBe('/api/v1/authoring/import-scripts');
        expect(init.method).toBe('POST');
        expect(init.body.get('projectSlug')).toBe('proj');
        expect(init.body.get('folder')).toBe('onboarding');
        expect(init.body.get('replace')).toBe('true');
        expect(init.body.getAll('files').map((f) => f.name)).toEqual(['welcome.dlb']);
        expect(results[0].dialogueName).toBe('onboarding/welcome');
    });

    it('does not replace by default and imports into the root for an empty folder', async () => {
        const { client, fetch } = clientReturning([]);
        await client.importScripts('proj', '', [new File([''], 'a.dlb')]);
        const init = fetch.mock.calls[0][1];
        expect(init.body.get('replace')).toBe('false');
        expect(init.body.get('folder')).toBe('');
    });
});
