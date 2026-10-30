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
import { DialogueBranchAuthoringClient } from './DialogueBranchAuthoringClient.js';
import { SupportedVariable } from './model/SupportedVariable.js';

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('DialogueBranchAuthoringClient.listSupportedVariables', () => {
    it('returns SupportedVariables with the service\'s hints', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify([
            { name: 'dayPart', type: 'string', values: ['morning', 'night'], readOnly: true },
            { name: 'mood' },
        ]), { status: 200, headers: { 'content-type': 'application/json' } })));

        const supported = await new DialogueBranchAuthoringClient({ baseUrl: '/api/v1' })
            .listSupportedVariables('default-test');

        expect(supported).toHaveLength(2);
        expect(supported[0]).toBeInstanceOf(SupportedVariable);
        expect(supported[0].readOnly).toBe(true);
        expect(supported[0].values).toEqual(['morning', 'night']);
        expect(supported[1].readOnly).toBeNull();
    });
});

describe('DialogueBranchAuthoringClient.getExternalVariableServiceStatus', () => {
    it('asks the Web Service whether the External Variable Service is running', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(
            { state: 'UNREACHABLE', checkedAt: 1793300000000 }),
            { status: 200, headers: { 'content-type': 'application/json' } }));
        vi.stubGlobal('fetch', fetchMock);

        const status = await new DialogueBranchAuthoringClient({ baseUrl: '/api/v1' })
            .getExternalVariableServiceStatus();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/info/external-variable-service');
        expect(status).toEqual({ state: 'UNREACHABLE', checkedAt: 1793300000000 });
    });
});
