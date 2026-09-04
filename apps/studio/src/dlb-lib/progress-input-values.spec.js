import { describe, it, expect, vi, afterEach } from 'vitest';
import { DialogueBranchClient } from './DialogueBranchClient.js';

function jsonResponse(payload) {
    return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'content-type': 'application/json' },
    });
}

const NEXT_STEP = {
    value: {
        dialogue: 'inputs', node: 'Next', speaker: 'Martin McOwl',
        loggedDialogueId: 'ld', loggedInteractionIndex: 1,
        statement: { segments: [{ segmentType: 'TEXT', text: 'thanks' }] },
        replies: [],
    },
};

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('progressDialogue / progressDraftDialogue with input values', () => {
    it('sends the collected values as the JSON request body', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(NEXT_STEP));
        vi.stubGlobal('fetch', fetchMock);

        const client = new DialogueBranchClient('/api/v1');
        await client.progressDialogue('ld', 0, 3, { firstName: 'Robin', age: 41 });

        const [, options] = fetchMock.mock.calls[0];
        expect(options.method).toBe('POST');
        expect(JSON.parse(options.body)).toEqual({ firstName: 'Robin', age: 41 });
    });

    it('omits the body when no input values are given', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(NEXT_STEP));
        vi.stubGlobal('fetch', fetchMock);

        const client = new DialogueBranchClient('/api/v1');
        await client.progressDialogue('ld', 0, 3);

        expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
    });

    it('progressDraftDialogue also forwards the values', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse(NEXT_STEP));
        vi.stubGlobal('fetch', fetchMock);

        const client = new DialogueBranchClient('/api/v1');
        await client.progressDraftDialogue('sess-1', 3, { wantsCheese: true });

        expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ wantsCheese: true });
    });
});
