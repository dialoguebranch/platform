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
import { downloadBlob } from './download-blob.js';

describe('downloadBlob', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        document.body.innerHTML = '';
    });

    it('clicks a temporary link to the blob, named as asked, and cleans up after itself', () => {
        const create = vi.fn(() => 'blob:fake-url');
        const revoke = vi.fn();
        vi.stubGlobal('URL', { createObjectURL: create, revokeObjectURL: revoke });
        const clicks = [];
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
            clicks.push({ href: this.getAttribute('href'), download: this.download,
                inDocument: document.body.contains(this) });
        });
        const blob = new Blob(['x']);

        downloadBlob(blob, 'main.dlb');

        expect(create).toHaveBeenCalledWith(blob);
        expect(clicks).toEqual([{ href: 'blob:fake-url', download: 'main.dlb', inDocument: true }]);
        expect(revoke).toHaveBeenCalledWith('blob:fake-url');
        expect(document.querySelector('a')).toBeNull();
        vi.unstubAllGlobals();
    });
});
