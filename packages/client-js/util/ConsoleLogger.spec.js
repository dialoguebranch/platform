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
    ])('routes %s to the corresponding console method', (method, level) => {
        const output = vi.spyOn(console, method).mockImplementation(() => {});
        const fallback = vi.spyOn(console, 'log').mockImplementation(() => {});

        new ConsoleLogger(LOG_LEVEL_DEBUG)[method]('client', 'message');

        expect(output).toHaveBeenCalledExactlyOnceWith(`[client - ${level}] message`);
        expect(fallback).not.toHaveBeenCalled();
    });

    it('keeps debug messages suppressed at the info log level', () => {
        const output = vi.spyOn(console, 'debug').mockImplementation(() => {});
        const fallback = vi.spyOn(console, 'log').mockImplementation(() => {});

        new ConsoleLogger(LOG_LEVEL_INFO).debug('client', 'hidden');

        expect(output).not.toHaveBeenCalled();
        expect(fallback).not.toHaveBeenCalled();
    });

    it.each(['TRACE', 'toString', 'constructor', '__proto__'])('falls back to console.log for %s', (level) => {
        const output = vi.spyOn(console, 'log').mockImplementation(() => {});

        new ConsoleLogger(LOG_LEVEL_INFO).writeLogEntry(level, 'client', 'message');

        expect(output).toHaveBeenCalledExactlyOnceWith(`[client - ${level}] message`);
    });
});
