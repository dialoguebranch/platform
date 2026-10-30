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


import { describe, it, expect, vi } from 'vitest';
import { DialogueBranchAuthoringClient } from './DialogueBranchAuthoringClient.js';

// updateProjectDraft sends the whole Save Draft batch, the speakers included (#395).

function clientReturning(payload) {
    const fetch = vi.fn(async () => new Response(JSON.stringify(payload), {
        status: 200, headers: { 'Content-Type': 'application/json' },
    }));
    return { client: new DialogueBranchAuthoringClient({ baseUrl: '/api/v1', fetch }), fetch };
}

describe('updateProjectDraft', () => {
    it('sends the speakers with the rest of the batch', async () => {
        const { client, fetch } = clientReturning({ slug: 'p' });
        const speakers = [{ name: 'Robin', gender: 'female', default: true }];
        await client.updateProjectDraft('p', { displayName: 'P', speakers });

        const [url, init] = fetch.mock.calls[0];
        expect(url).toBe('/api/v1/project/update-draft?projectSlug=p');
        expect(JSON.parse(init.body)).toEqual({ displayName: 'P', speakers });
    });

    it('leaves the speakers out when they aren\'t given', async () => {
        const { client, fetch } = clientReturning({ slug: 'p' });
        await client.updateProjectDraft('p', { description: 'D' });
        expect(JSON.parse(fetch.mock.calls[0][1].body)).not.toHaveProperty('speakers');
    });
});

describe('findSpeakerReferences', () => {
    it('asks for the speaker, encoded', async () => {
        const { client, fetch } = clientReturning({ nodes: 2, dialogues: 1, translations: 0 });
        const result = await client.findSpeakerReferences('p', 'Martin McOwl');

        expect(fetch.mock.calls[0][0])
            .toBe('/api/v1/project/find-speaker-references?projectSlug=p&speaker=Martin%20McOwl');
        expect(result).toEqual({ nodes: 2, dialogues: 1, translations: 0 });
    });
});
