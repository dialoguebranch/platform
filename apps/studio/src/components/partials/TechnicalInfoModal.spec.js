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

// The External Variable Service section of Technical Information (#430).

const authoring = { getTechnicalInfo: vi.fn() };

vi.mock('@/composables/client.js', () => ({
    useAuthoringClient: () => authoring,
    getBrowserTimeZone: () => 'UTC',
}));

const { default: TechnicalInfoModal } = await import('./TechnicalInfoModal.vue');

const BASE = { serviceVersion: '0.8.0', buildTime: 'now', configuredBaseUrl: 'http://x',
    keycloakBaseUrl: 'http://kc', keycloakRealm: 'r', activeUserServiceCount: 1 };

async function mountWith(externalVariableService) {
    authoring.getTechnicalInfo.mockResolvedValue({ ...BASE, externalVariableService });
    const wrapper = mount(TechnicalInfoModal, {
        global: { stubs: { FontAwesomeIcon: true, Teleport: true } },
    });
    await flushPromises();
    return wrapper.get('[data-test="technical-evs"]').text();
}

beforeEach(() => vi.clearAllMocks());

describe('TechnicalInfoModal — External Variable Service', () => {
    it('shows where it is and how the calls to it went', async () => {
        const text = await mountWith({ enabled: true, url: 'http://evs:8090',
            lastSuccess: Date.UTC(2026, 9, 30, 9, 0), lastFailure: Date.UTC(2026, 9, 30, 10, 0),
            lastError: 'Connection refused', failuresInARow: 3 });
        expect(text).toContain('http://evs:8090');
        expect(text).toContain('Connection refused');
        expect(text).toContain('Failed calls in a row: 3');
    });

    it('says when there has been no call yet', async () => {
        const text = await mountWith({ enabled: true, url: 'http://evs:8090', lastSuccess: null,
            lastFailure: null, lastError: null, failuresInARow: 0 });
        expect(text).toContain('Last successful call: none yet');
        expect(text).not.toContain('Last error');
    });

    it('says when none is configured', async () => {
        expect(await mountWith({ enabled: false, url: null, failuresInARow: 0 }))
            .toContain('Not configured');
    });
});
