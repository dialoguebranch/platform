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
import {
    HISTORY_PAGE_SIZE, sourceLabel, formatTimestamp, formatHistoryValue, mergeHistoryPage,
    mayHaveMore, resolveExactName, filterSortHistory, needsLoadedOnlyHint,
    HISTORY_PRESETS, zonedInputToEpoch, epochToZonedInput, presetRange, resolveDateRange,
} from './variable-history.js';

const entry = (id, name, value = 'x') => ({ id, name, value });

describe('sourceLabel', () => {
    it('labels every known source', () => {
        expect(sourceLabel('DLB_SCRIPT').label).toBe('Script');
        expect(sourceLabel('INPUT_REPLY').label).toBe('User Input');
        expect(sourceLabel('WEB_SERVICE').label).toBe('Web Service');
        expect(sourceLabel('EXTERNAL').label).toBe('External Service');
    });

    it('falls back to an italic "Unknown" for missing or unrecognised sources', () => {
        for (const source of [undefined, null, 'SOMETHING_NEW', 'UNKNOWN']) {
            expect(sourceLabel(source)).toEqual({ label: 'Unknown', italic: true });
        }
    });
});

describe('formatTimestamp', () => {
    // 2026-10-11T11:46:38Z — 12:46 in Lisbon (WEST, UTC+1), 20:46 in Tokyo (UTC+9).
    const T = Date.UTC(2026, 9, 11, 11, 46, 38);

    it('formats in the given (Studio user\'s) time zone, not the recorded one', () => {
        const style = { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' };
        expect(formatTimestamp(T, 'Europe/Lisbon', style)).toBe('12:46');
        expect(formatTimestamp(T, 'Asia/Tokyo', style)).toBe('20:46');
    });

    it('returns null when there is no timestamp', () => {
        expect(formatTimestamp(null, 'UTC')).toBeNull();
        expect(formatTimestamp(undefined, 'UTC')).toBeNull();
    });

    it('formats 0 (the epoch) rather than treating it as missing', () => {
        expect(formatTimestamp(0, 'UTC')).not.toBeNull();
    });
});

describe('formatHistoryValue', () => {
    it('returns null for a removal', () => {
        expect(formatHistoryValue(null)).toBeNull();
        expect(formatHistoryValue(undefined)).toBeNull();
    });

    it('shows strings as-is and everything else as JSON', () => {
        expect(formatHistoryValue('fine')).toBe('fine');
        expect(formatHistoryValue('')).toBe('');
        expect(formatHistoryValue(42)).toBe('42');
        expect(formatHistoryValue(false)).toBe('false');
        expect(formatHistoryValue({ week: 3 })).toBe('{"week":3}');
        expect(formatHistoryValue([1, 2])).toBe('[1,2]');
    });
});

describe('mergeHistoryPage', () => {
    it('appends a page in order', () => {
        const merged = mergeHistoryPage([entry(9, 'a'), entry(8, 'b')], [entry(7, 'c'), entry(6, 'd')]);
        expect(merged.map((e) => e.id)).toEqual([9, 8, 7, 6]);
    });

    it('drops entries the page repeats — what happens when new rows shift the offset pages', () => {
        // Page 0 was [9, 8]; then 2 new rows (11, 10) were recorded, so page 1 is now [9, 8].
        const merged = mergeHistoryPage([entry(9, 'a'), entry(8, 'b')], [entry(9, 'a'), entry(8, 'b')]);
        expect(merged.map((e) => e.id)).toEqual([9, 8]);

        // Or a partial overlap: page 1 shifted by one.
        const partly = mergeHistoryPage([entry(9, 'a'), entry(8, 'b')], [entry(8, 'b'), entry(7, 'c')]);
        expect(partly.map((e) => e.id)).toEqual([9, 8, 7]);
    });

    it('does not modify its inputs', () => {
        const loaded = [entry(2, 'a')];
        const page = [entry(1, 'b')];
        mergeHistoryPage(loaded, page);
        expect(loaded).toHaveLength(1);
        expect(page).toHaveLength(1);
    });
});

describe('mayHaveMore', () => {
    it('is true for a full page and false for a short one', () => {
        expect(mayHaveMore(new Array(HISTORY_PAGE_SIZE).fill(entry(1, 'a')), HISTORY_PAGE_SIZE)).toBe(true);
        expect(mayHaveMore([entry(1, 'a')], HISTORY_PAGE_SIZE)).toBe(false);
        expect(mayHaveMore([], HISTORY_PAGE_SIZE)).toBe(false);
    });
});

describe('resolveExactName', () => {
    const known = ['mood', 'moodScore', 'currentTime'];

    it('returns a known name typed in full', () => {
        expect(resolveExactName('mood', known)).toBe('mood');
        expect(resolveExactName('  currentTime  ', known)).toBe('currentTime');
    });

    it('ignores a leading $, since that\'s how names are displayed', () => {
        expect(resolveExactName('$mood', known)).toBe('mood');
    });

    it('returns null for partial, unknown or empty text', () => {
        expect(resolveExactName('mo', known)).toBeNull();
        expect(resolveExactName('moods', known)).toBeNull();
        expect(resolveExactName('', known)).toBeNull();
        expect(resolveExactName('   ', known)).toBeNull();
        expect(resolveExactName('$', known)).toBeNull();
        expect(resolveExactName(undefined, known)).toBeNull();
    });

    it('matches case-insensitively but returns the name as spelled', () => {
        expect(resolveExactName('CURRENTTIME', known)).toBe('currentTime');
    });

    it('prefers an exact-case match when two names differ only by case', () => {
        expect(resolveExactName('Mood', ['mood', 'Mood'])).toBe('Mood');
        expect(resolveExactName('mood', ['Mood', 'mood'])).toBe('mood');
    });

    it('accepts any iterable of names, e.g. a Set', () => {
        expect(resolveExactName('mood', new Set(known))).toBe('mood');
    });
});

describe('filterSortHistory', () => {
    const loaded = [entry(5, 'mood'), entry(4, 'currentTime'), entry(3, 'moodScore'), entry(2, 'mood')];

    it('keeps everything newest first by default', () => {
        expect(filterSortHistory(loaded).map((e) => e.id)).toEqual([5, 4, 3, 2]);
    });

    it('reverses to oldest first', () => {
        expect(filterSortHistory(loaded, { order: 'oldest' }).map((e) => e.id)).toEqual([2, 3, 4, 5]);
    });

    it('filters a partial name case-insensitively, ignoring a leading $', () => {
        expect(filterSortHistory(loaded, { query: 'MOOD' }).map((e) => e.id)).toEqual([5, 3, 2]);
        expect(filterSortHistory(loaded, { query: '$time' }).map((e) => e.id)).toEqual([4]);
    });

    it('does not filter again when the server already filtered to an exact name', () => {
        // The query 'mood' would also match moodScore client-side — but with exactName set, the
        // loaded entries are already only 'mood' ones from the server, so they're kept as-is.
        const serverFiltered = [entry(5, 'mood'), entry(2, 'mood')];
        expect(filterSortHistory(serverFiltered, { query: 'mood', exactName: 'mood' })
            .map((e) => e.id)).toEqual([5, 2]);
    });

    it('does not modify its input', () => {
        const copy = [...loaded];
        filterSortHistory(loaded, { order: 'oldest', query: 'mood' });
        expect(loaded).toEqual(copy);
    });
});

describe('needsLoadedOnlyHint', () => {
    it('is never needed when everything is loaded', () => {
        expect(needsLoadedOnlyHint({ query: 'mo', order: 'oldest', hasMore: false })).toBe(false);
    });

    it('is needed for a partial-name search with more pages available', () => {
        expect(needsLoadedOnlyHint({ query: 'mo', hasMore: true })).toBe(true);
    });

    it('is needed for oldest-first order with more pages available', () => {
        expect(needsLoadedOnlyHint({ order: 'oldest', hasMore: true })).toBe(true);
    });

    it('is not needed newest-first without a search, or for an exact-name search', () => {
        expect(needsLoadedOnlyHint({ hasMore: true })).toBe(false);
        expect(needsLoadedOnlyHint({ query: '   ', hasMore: true })).toBe(false);
        expect(needsLoadedOnlyHint({ query: 'mood', exactName: 'mood', hasMore: true })).toBe(false);
    });

    it('is still needed for an exact-name search shown oldest first', () => {
        expect(needsLoadedOnlyHint({ query: 'mood', exactName: 'mood', order: 'oldest', hasMore: true }))
            .toBe(true);
    });
});

// -------------------- time range (#371) -------------------- //

const iso = (millis) => new Date(millis).toISOString();

describe('zonedInputToEpoch', () => {
    it('reads a local date-time in the given zone, with or without seconds', () => {
        // Lisbon is UTC+1 in October (summer time), Tokyo UTC+9, São Paulo UTC-3, Kolkata UTC+5:30.
        expect(iso(zonedInputToEpoch('2026-10-14T09:30', 'Europe/Lisbon'))).toBe('2026-10-14T08:30:00.000Z');
        expect(iso(zonedInputToEpoch('2026-10-14T09:30:15', 'Europe/Lisbon'))).toBe('2026-10-14T08:30:15.000Z');
        expect(iso(zonedInputToEpoch('2026-10-14T09:30', 'Asia/Tokyo'))).toBe('2026-10-14T00:30:00.000Z');
        expect(iso(zonedInputToEpoch('2026-10-14T09:30', 'America/Sao_Paulo'))).toBe('2026-10-14T12:30:00.000Z');
        expect(iso(zonedInputToEpoch('2026-10-14T09:30', 'Asia/Kolkata'))).toBe('2026-10-14T04:00:00.000Z');
    });

    it('uses the zone\'s winter offset in winter', () => {
        expect(iso(zonedInputToEpoch('2026-01-14T09:30', 'Europe/Lisbon'))).toBe('2026-01-14T09:30:00.000Z');
    });

    it('returns null for empty, malformed and impossible values', () => {
        for (const bad of [null, undefined, '', '  ', 'yesterday', '2026-10-14', '2026-10-14 09:30',
                '2026-02-31T10:00', '2026-13-01T10:00', '2026-10-14T24:00', '2026-10-14T10:60']) {
            expect(zonedInputToEpoch(bad, 'Europe/Lisbon'), String(bad)).toBeNull();
        }
    });

    it('reads a time that happens twice at a daylight-saving change as its first occurrence', () => {
        // Lisbon: clocks go back 02:00 → 01:00 on 2026-10-25, so 01:30 happens at 00:30Z and 01:30Z.
        expect(iso(zonedInputToEpoch('2026-10-25T01:30', 'Europe/Lisbon'))).toBe('2026-10-25T00:30:00.000Z');
    });

    it('reads a skipped time with the offset from before the change', () => {
        // Lisbon: clocks go forward 01:00 → 02:00 on 2026-03-29, so 01:30 never happens.
        expect(iso(zonedInputToEpoch('2026-03-29T01:30', 'Europe/Lisbon'))).toBe('2026-03-29T01:30:00.000Z');
    });

    it('round-trips through epochToZonedInput', () => {
        for (const zone of ['Europe/Lisbon', 'Asia/Tokyo', 'America/Sao_Paulo', 'Asia/Kolkata']) {
            const epoch = zonedInputToEpoch('2026-10-14T09:30:15', zone);
            expect(epochToZonedInput(epoch, zone), zone).toBe('2026-10-14T09:30:15');
        }
    });
});

describe('presetRange', () => {
    it('always leaves the end date empty, so entries recorded afterwards still show up', () => {
        for (const preset of HISTORY_PRESETS) {
            expect(presetRange(preset.id, Date.now(), 'Asia/Tokyo').end, preset.id).toBe('');
        }
    });

    it('"today" is the date in the Studio user\'s zone, not in UTC', () => {
        // 2026-10-14T20:00Z is already the 15th, 05:00, in Tokyo — and still the 14th in Lisbon.
        const instant = Date.UTC(2026, 9, 14, 20, 0, 0);
        expect(presetRange('today', instant, 'Asia/Tokyo').start).toBe('2026-10-15');
        expect(presetRange('today', instant, 'Europe/Lisbon').start).toBe('2026-10-14');
        expect(presetRange('today', instant, 'America/Sao_Paulo').start).toBe('2026-10-14');
        // Just after local midnight, and just before it.
        expect(presetRange('today', zonedInputToEpoch('2026-10-14T00:00:01', 'Europe/Lisbon'), 'Europe/Lisbon').start).toBe('2026-10-14');
        expect(presetRange('today', zonedInputToEpoch('2026-10-13T23:59:59', 'Europe/Lisbon'), 'Europe/Lisbon').start).toBe('2026-10-13');
    });

    it('"7days" is today and the 6 days before it; "30days" today and the 29 before it', () => {
        const instant = zonedInputToEpoch('2026-10-14T12:00:00', 'Europe/Lisbon');
        expect(presetRange('7days', instant, 'Europe/Lisbon').start).toBe('2026-10-08');
        expect(presetRange('30days', instant, 'Europe/Lisbon').start).toBe('2026-09-15');
    });

    it('counts back across month and year boundaries', () => {
        const instant = zonedInputToEpoch('2026-01-03T12:00:00', 'Europe/Lisbon');
        expect(presetRange('7days', instant, 'Europe/Lisbon').start).toBe('2025-12-28');
        expect(presetRange('30days', instant, 'Europe/Lisbon').start).toBe('2025-12-05');
    });

    it('rejects an unknown preset', () => {
        expect(() => presetRange('fortnight', Date.now(), 'Asia/Tokyo')).toThrow();
    });
});

describe('resolveDateRange', () => {
    const zone = 'Asia/Tokyo';

    it('is unbounded when both dates are empty', () => {
        expect(resolveDateRange('', '', zone)).toEqual({ from: undefined, to: undefined, error: null });
    });

    it('covers whole days: the start\'s midnight up to the midnight after the end date', () => {
        // October 5 to October 6, in Tokyo (UTC+9): from Oct 5 00:00 JST up to Oct 7 00:00 JST.
        const range = resolveDateRange('2026-10-05', '2026-10-06', zone);
        expect(range.error).toBeNull();
        expect(iso(range.from)).toBe('2026-10-04T15:00:00.000Z');
        expect(iso(range.to)).toBe('2026-10-06T15:00:00.000Z');
    });

    it('includes the whole end date: an entry at 23:59:59 on it is in, one at midnight after is out', () => {
        const range = resolveDateRange('2026-10-05', '2026-10-06', zone);
        const lastSecond = zonedInputToEpoch('2026-10-06T23:59:59', zone);
        const nextMidnight = zonedInputToEpoch('2026-10-07T00:00:00', zone);
        expect(lastSecond >= range.from && lastSecond < range.to).toBe(true);
        expect(nextMidnight < range.to).toBe(false);
    });

    it('excludes what was recorded on another day (the October 5 to 6 case)', () => {
        const range = resolveDateRange('2026-10-05', '2026-10-06', 'Europe/Lisbon');
        const entry = zonedInputToEpoch('2026-10-14T18:09:15', 'Europe/Lisbon');
        expect(entry >= range.from && entry < range.to).toBe(false);
    });

    it('takes either date on its own', () => {
        const startOnly = resolveDateRange('2026-10-05', '', zone);
        expect(iso(startOnly.from)).toBe('2026-10-04T15:00:00.000Z');
        expect(startOnly.to).toBeUndefined();
        const endOnly = resolveDateRange('', '2026-10-06', zone);
        expect(endOnly.from).toBeUndefined();
        expect(iso(endOnly.to)).toBe('2026-10-06T15:00:00.000Z');
    });

    it('treats the same date as start and end as that one whole day', () => {
        const range = resolveDateRange('2026-10-05', '2026-10-05', zone);
        expect(range.error).toBeNull();
        expect(range.to - range.from).toBe(24 * 60 * 60 * 1000);
    });

    it('lets a day with a daylight-saving change be 23 or 25 hours long', () => {
        // Lisbon: clocks go back on 2026-10-25 (25 hours) and forward on 2026-03-29 (23 hours).
        const back = resolveDateRange('2026-10-25', '2026-10-25', 'Europe/Lisbon');
        const forward = resolveDateRange('2026-03-29', '2026-03-29', 'Europe/Lisbon');
        expect((back.to - back.from) / 3600000).toBe(25);
        expect((forward.to - forward.from) / 3600000).toBe(23);
    });

    it('rolls the day after the end date over month and year ends', () => {
        const range = resolveDateRange('2026-12-30', '2026-12-31', 'Europe/Lisbon');
        expect(iso(range.to)).toBe('2027-01-01T00:00:00.000Z');
    });

    it('reports a start date after the end date, and sends neither bound', () => {
        const result = resolveDateRange('2026-10-06', '2026-10-05', zone);
        expect(result.error).toMatch(/start date must not be after the end date/i);
        expect(result.from).toBeUndefined();
        expect(result.to).toBeUndefined();
    });

    it('reports a value that is not a valid date', () => {
        expect(resolveDateRange('nonsense', '', zone).error).toMatch(/start date/i);
        expect(resolveDateRange('', '2026-02-31', zone).error).toMatch(/end date/i);
        expect(resolveDateRange('2026-10-05T10:00', '', zone).error).toMatch(/start date/i);
    });
});
