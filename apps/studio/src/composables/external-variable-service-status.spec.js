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

import { describe, it, expect } from 'vitest';
import { statusLabel } from './external-variable-service-status.js';

// 2026-10-30T11:05:00Z — 12:05 in Amsterdam.
const CHECKED_AT = Date.UTC(2026, 9, 30, 11, 5, 0);

describe('statusLabel', () => {
    it('says the service is connected, with the time of the check', () => {
        const label = statusLabel({ state: 'REACHABLE', checkedAt: CHECKED_AT }, 'Europe/Amsterdam');
        expect(label.text).toBe('External Variable Service: connected');
        expect(label.title).toContain('checked at 12:05');
        expect(label.unreachable).toBe(false);
    });

    it('says the service is not reachable, and where admins find more', () => {
        const label = statusLabel({ state: 'UNREACHABLE', checkedAt: CHECKED_AT }, 'Europe/Amsterdam');
        expect(label.text).toBe('External Variable Service: not reachable');
        expect(label.title).toContain('Technical Information');
        expect(label.unreachable).toBe(true);
    });

    it('shows nothing when no service is configured, or the status isn\'t known', () => {
        expect(statusLabel({ state: 'NOT_CONFIGURED', checkedAt: null }, 'UTC')).toBeNull();
        expect(statusLabel(null, 'UTC')).toBeNull();
    });
});
