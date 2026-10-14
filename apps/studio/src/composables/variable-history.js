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

// Pure helpers behind the Variable Browser's "History" mode (#323) — no Vue, no network, so each
// rule here is unit-testable on its own (see variable-history.spec.js). The component wires them
// to the client and its own reactive state.
//
// The one thing to keep in mind throughout: history pages come from the server, newest first, by
// offset. Studio only ever holds the pages it has loaded so far, so anything done *in the browser*
// (a partial-name filter, oldest-first order) only covers those loaded entries — see
// needsLoadedOnlyHint().

/** Entries requested per "page" — the end-point's own default. */
export const HISTORY_PAGE_SIZE = 50;

/**
 * Display labels for a change's source, shared by the "Values" tooltip and the "History" rows.
 * `italic` marks the "we don't know" case.
 */
export const SOURCE_LABELS = {
    UNKNOWN:      { label: 'Unknown',          italic: true },
    DLB_SCRIPT:   { label: 'Script',           italic: false },
    INPUT_REPLY:  { label: 'User Input',       italic: false },
    WEB_SERVICE:  { label: 'Web Service',      italic: false },
    EXTERNAL:     { label: 'External Service', italic: false },
};

/**
 * Returns the display label for a source value, falling back to "Unknown" for anything missing
 * or unrecognised.
 *
 * @param {string|null|undefined} source e.g. `DLB_SCRIPT`.
 * @returns {{label: string, italic: boolean}}
 */
export function sourceLabel(source) {
    return SOURCE_LABELS[source] ?? SOURCE_LABELS.UNKNOWN;
}

/**
 * Formats an epoch-millisecond timestamp in the given time zone — always the time zone of the
 * person using Studio (#323), never the zone the value was recorded in.
 *
 * @param {number|null|undefined} epochMillis the timestamp.
 * @param {string} timeZone an IANA time zone, e.g. `Europe/Lisbon`.
 * @param {Intl.DateTimeFormatOptions} [style] defaults to a compact date + time.
 * @returns {string|null} the formatted time, or `null` if there is no timestamp.
 */
export function formatTimestamp(epochMillis, timeZone,
        style = { dateStyle: 'short', timeStyle: 'medium' }) {
    if (epochMillis == null) return null;
    return new Date(epochMillis).toLocaleString(undefined, { ...style, timeZone });
}

/**
 * Formats a history entry's value for display: strings as-is, anything structured as JSON.
 * Returns `null` for a removal — the template renders that as "removed".
 *
 * @param {*} value the entry's value.
 * @returns {string|null}
 */
export function formatHistoryValue(value) {
    if (value === null || value === undefined) return null;
    return typeof value === 'string' ? value : JSON.stringify(value);
}

/**
 * Appends a newly loaded page to the entries already shown, dropping any it repeats. Pages are by
 * offset, so when new changes are recorded between two requests, the next page starts with
 * entries the previous one already had — each entry's `id` is what recognises them.
 *
 * @param {Array<{id: number}>} loaded the entries already shown.
 * @param {Array<{id: number}>} page the newly loaded page.
 * @returns {Array<{id: number}>} a new array: `loaded` followed by the page's unseen entries.
 */
export function mergeHistoryPage(loaded, page) {
    const seen = new Set(loaded.map((entry) => entry.id));
    return [...loaded, ...page.filter((entry) => !seen.has(entry.id))];
}

/**
 * Whether the server may have more entries after a page: a full page means "maybe", a short one
 * means "no". (A full last page costs one extra, empty request — the end-point doesn't report a
 * total.)
 *
 * @param {Array} page the page just loaded.
 * @param {number} pageSize the page size it was requested with.
 * @returns {boolean}
 */
export function mayHaveMore(page, pageSize) {
    return page.length >= pageSize;
}

/**
 * Resolves the search box's text to an exact, known variable name — the case in which the server
 * is asked for that one variable's history (#323), so it pages correctly however busy the other
 * variables are. Anything else (empty, partial, or unknown text) returns `null`: the loaded
 * entries are then filtered in the browser instead.
 *
 * A leading `$` is ignored, since that's how names are displayed. Matching is case-insensitive,
 * but returns the name as it's actually spelled, preferring an exact-case match if two known names
 * differ only by case.
 *
 * @param {string} query the search box's text.
 * @param {Iterable<string>} knownNames every variable name Studio knows of for this project.
 * @returns {string|null} the canonical variable name, or `null`.
 */
export function resolveExactName(query, knownNames) {
    const wanted = (query ?? '').trim().replace(/^\$/, '');
    if (!wanted) return null;
    let caseInsensitiveMatch = null;
    for (const name of knownNames) {
        if (name === wanted) return name;
        if (caseInsensitiveMatch === null && name.toLowerCase() === wanted.toLowerCase())
            caseInsensitiveMatch = name;
    }
    return caseInsensitiveMatch;
}

/**
 * Filters and orders the loaded entries for display.
 *
 * When `exactName` is set, the server has already filtered to that variable, so no further
 * filtering happens here. Otherwise a non-empty `query` keeps entries whose name contains it
 * (case-insensitive, leading `$` ignored).
 *
 * @param {Array<{id: number, name: string}>} entries the loaded entries, newest first.
 * @param {Object} options
 * @param {string} [options.query] the search box's text.
 * @param {string|null} [options.exactName] see {@link resolveExactName}.
 * @param {'newest'|'oldest'} [options.order] defaults to newest first.
 * @returns {Array} a new array.
 */
export function filterSortHistory(entries, { query = '', exactName = null, order = 'newest' } = {}) {
    const needle = query.trim().replace(/^\$/, '').toLowerCase();
    const filtered = exactName || !needle
        ? entries
        : entries.filter((entry) => entry.name.toLowerCase().includes(needle));
    const direction = order === 'oldest' ? 1 : -1;
    return [...filtered].sort((a, b) => direction * (a.id - b.id));
}

/**
 * Whether to show the "only loaded entries" hint: when what's displayed was narrowed or reordered
 * in the browser (a partial-name filter, or oldest-first order) and the server may still have
 * older entries that aren't loaded yet. Newest-first and exact-name views are complete as far as
 * they go, so they never need it.
 *
 * @param {Object} options
 * @param {string} [options.query] the search box's text.
 * @param {string|null} [options.exactName] see {@link resolveExactName}.
 * @param {'newest'|'oldest'} [options.order]
 * @param {boolean} options.hasMore see {@link mayHaveMore}.
 * @returns {boolean}
 */
export function needsLoadedOnlyHint({ query = '', exactName = null, order = 'newest', hasMore }) {
    if (!hasMore) return false;
    const partialSearch = !exactName && query.trim().replace(/^\$/, '').length > 0;
    return partialSearch || order === 'oldest';
}

// -------------------- time range (#371) -------------------- //
//
// The History view's start / end dates are <input type="date"> values ("2026-10-14"), whole days
// with no time and no zone. They are read in the Studio user's time zone — the same one the
// entries are displayed in — and sent to the server as epoch milliseconds: the start date's
// midnight (inclusive) up to the midnight after the end date (exclusive), so the end date is
// included in full (GET /variables/history, #352).

/** The quick presets offered next to the date inputs. Each one fills the start date and clears the end date. */
export const HISTORY_PRESETS = [
    { id: 'today', label: 'Today' },
    { id: '7days', label: 'Last 7 days' },
    { id: '30days', label: 'Last 30 days' },
];

const pad = (n, width = 2) => String(n).padStart(width, '0');

// The calendar fields of an instant, as seen in a time zone.
function zonedParts(epochMillis, timeZone) {
    const parts = new Intl.DateTimeFormat('en-US', {
        timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).formatToParts(new Date(epochMillis));
    const get = (type) => Number(parts.find((p) => p.type === type).value);
    return { year: get('year'), month: get('month'), day: get('day'),
        hour: get('hour'), minute: get('minute'), second: get('second') };
}

const toInputString = ({ year, month, day, hour, minute, second }) =>
    `${pad(year, 4)}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:${pad(second)}`;

// How far ahead of UTC a time zone is at an instant, in milliseconds.
function zoneOffset(epochMillis, timeZone) {
    const p = zonedParts(epochMillis, timeZone);
    const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
    return asUtc - Math.floor(epochMillis / 1000) * 1000;
}

const INPUT_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/;

/**
 * Converts a `datetime-local` value, read in a time zone, to epoch milliseconds.
 *
 * Around a daylight-saving change a local time can be skipped (the clocks jump over it) or happen
 * twice. A skipped time is read with the offset from before the change (so it lands an hour later
 * on the clock); an ambiguous one as its first occurrence.
 *
 * @param {string|null|undefined} value e.g. `2026-10-14T09:30` or `2026-10-14T09:30:15`.
 * @param {string} timeZone an IANA time zone, e.g. `Europe/Lisbon`.
 * @returns {number|null} the epoch milliseconds, or `null` if the value is empty or not a valid
 * date-time.
 */
export function zonedInputToEpoch(value, timeZone) {
    const match = INPUT_PATTERN.exec((value ?? '').trim());
    if (!match) return null;
    const [year, month, day, hour, minute, second] = match.slice(1).map((n) => Number(n ?? 0));
    const naive = Date.UTC(year, month - 1, day, hour, minute, second);
    const check = new Date(naive);
    // Date.UTC rolls over impossible dates (31 February); reject them instead.
    if (check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day
        || hour > 23 || minute > 59 || second > 59) return null;
    // The zone's offset a day before and a day after: they differ only across a daylight-saving
    // change, and each gives one candidate instant. Those that really show this local time are
    // the valid readings (two for an ambiguous time, one otherwise, none for a skipped one).
    const DAY = 24 * 60 * 60 * 1000;
    const before = naive - zoneOffset(naive - DAY, timeZone);
    const after = naive - zoneOffset(naive + DAY, timeZone);
    const wanted = toInputString({ year, month, day, hour, minute, second });
    const valid = [before, after].filter((c) => epochToZonedInput(c, timeZone) === wanted);
    return valid.length ? Math.min(...valid) : before;
}

/**
 * Formats an instant as a `datetime-local` value in a time zone.
 *
 * @param {number} epochMillis the instant.
 * @param {string} timeZone an IANA time zone.
 * @returns {string} e.g. `2026-10-14T09:30:15`.
 */
export function epochToZonedInput(epochMillis, timeZone) {
    return toInputString(zonedParts(epochMillis, timeZone));
}

const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

// A date string moved by a number of calendar days ("2026-10-31" + 1 → "2026-11-01"), or null if
// it is not a valid date.
function shiftDate(date, days) {
    const match = DATE_PATTERN.exec((date ?? '').trim());
    if (!match) return null;
    const [year, month, day] = match.slice(1).map(Number);
    const base = new Date(Date.UTC(year, month - 1, day));
    if (base.getUTCMonth() !== month - 1 || base.getUTCDate() !== day) return null;
    const moved = new Date(Date.UTC(year, month - 1, day + days));
    return `${pad(moved.getUTCFullYear(), 4)}-${pad(moved.getUTCMonth() + 1)}-${pad(moved.getUTCDate())}`;
}

/**
 * The start / end dates a preset stands for: always open-ended (the end date stays empty), so
 * entries recorded after the preset was picked still show up as the view reloads.
 *
 *  - `today`: from today;
 *  - `7days`: from 6 days ago, i.e. today and the 6 days before it;
 *  - `30days`: from 29 days ago, i.e. today and the 29 days before it.
 *
 * "Today" is the date in the Studio user's time zone.
 *
 * @param {string} presetId one of {@link HISTORY_PRESETS}'s ids.
 * @param {number} now the current time, in epoch milliseconds.
 * @param {string} timeZone the Studio user's IANA time zone.
 * @returns {{start: string, end: string}} `YYYY-MM-DD` dates; `end` is always empty.
 */
export function presetRange(presetId, now, timeZone) {
    const p = zonedParts(now, timeZone);
    const today = `${pad(p.year, 4)}-${pad(p.month)}-${pad(p.day)}`;
    switch (presetId) {
        case 'today':  return { start: today, end: '' };
        case '7days':  return { start: shiftDate(today, -6), end: '' };
        case '30days': return { start: shiftDate(today, -29), end: '' };
        default: throw new Error('Unknown history preset: ' + presetId);
    }
}

/**
 * Turns the start / end date inputs into what is sent to the server, validating them first so that
 * an impossible range never becomes a request. The range covers whole days in the time zone: from
 * the start date's midnight up to (not including) the midnight after the end date.
 *
 * @param {string} start the start date input's value (empty for no lower bound).
 * @param {string} end the end date input's value (empty for no upper bound).
 * @param {string} timeZone the Studio user's IANA time zone.
 * @returns {{from: number|undefined, to: number|undefined, error: string|null}} the bounds in
 * epoch milliseconds (`undefined` where left empty), and a message if the range is not valid.
 * The same date as start and end is a single whole day.
 */
export function resolveDateRange(start, end, timeZone) {
    const startEmpty = !(start ?? '').trim();
    const endEmpty = !(end ?? '').trim();
    const startDate = startEmpty ? undefined : shiftDate(start, 0);
    const endDate = endEmpty ? undefined : shiftDate(end, 0);
    const none = { from: undefined, to: undefined };
    if (startDate === null) return { ...none, error: 'The start date is not a valid date.' };
    if (endDate === null) return { ...none, error: 'The end date is not a valid date.' };
    if (startDate !== undefined && endDate !== undefined && startDate > endDate)
        return { ...none, error: 'The start date must not be after the end date.' };
    return {
        from: startDate === undefined ? undefined : zonedInputToEpoch(startDate + 'T00:00:00', timeZone),
        to: endDate === undefined ? undefined : zonedInputToEpoch(shiftDate(endDate, 1) + 'T00:00:00', timeZone),
        error: null,
    };
}
