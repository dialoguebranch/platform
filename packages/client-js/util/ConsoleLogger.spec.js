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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConsoleLogger } from './ConsoleLogger.js';
import { LOG_LEVEL_DEBUG, LOG_LEVEL_INFO } from './AbstractLogger.js';

describe('ConsoleLogger', () => {
    afterEach(() => vi.restoreAllMocks());

    it.each([
        ['info', 'INFO'],
        ['warn', 'WARN'],
        ['error', 'ERROR'],
        ['debug', 'DEBUG'],
    ])('routes %s to the matching console method', (method, level) => {
        const output = vi.spyOn(console, method).mockImplementation(() => {});
        const fallback = vi.spyOn(console, 'log').mockImplementation(() => {});

        new ConsoleLogger(LOG_LEVEL_DEBUG)[method]('client', 'message');

        expect(output).toHaveBeenCalledExactlyOnceWith(`[client - ${level}] message`);
        expect(fallback).not.toHaveBeenCalled();
    });

    it('falls back to console.log for an unrecognized level, including prototype-chain-shaped ones', () => {
        for (const level of ['TRACE', 'toString', 'constructor', '__proto__']) {
            const output = vi.spyOn(console, 'log').mockImplementation(() => {});

            new ConsoleLogger(LOG_LEVEL_INFO).writeLogEntry(level, 'client', 'message');

            expect(output).toHaveBeenCalledExactlyOnceWith(`[client - ${level}] message`);
            vi.restoreAllMocks();
        }
    });

    it('still suppresses debug messages below the DEBUG log level', () => {
        const output = vi.spyOn(console, 'debug').mockImplementation(() => {});
        const fallback = vi.spyOn(console, 'log').mockImplementation(() => {});

        new ConsoleLogger(LOG_LEVEL_INFO).debug('client', 'hidden');

        expect(output).not.toHaveBeenCalled();
        expect(fallback).not.toHaveBeenCalled();
    });
});
