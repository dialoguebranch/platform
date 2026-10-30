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

// Pure helpers behind the External Variable Service part of Studio's status bar (#430): what it
// says for the Web Service's `GET /info/external-variable-service` answer. No Vue, no network, so
// each rule is unit-testable on its own (see external-variable-service-status.spec.js).

/** At most this often (in milliseconds) does Studio ask again, when it comes back into view. */
export const STATUS_REFRESH_INTERVAL = 30 * 1000;

/**
 * Returns what the status bar shows for the service's status, or `null` to show nothing: when no
 * service is configured, or the status isn't known (the request failed).
 *
 * @param {?{state: string, checkedAt: ?number}} status the Web Service's answer, or null.
 * @param {string} timeZone the IANA time zone to show the time of the check in.
 * @returns {?{text: string, title: string, unreachable: boolean}} the label, its tooltip, and
 * whether to mark it with a warning.
 */
export function statusLabel(status, timeZone) {
    if (!status || (status.state !== 'REACHABLE' && status.state !== 'UNREACHABLE')) return null;
    const checked = status.checkedAt
        ? ' (checked at ' + new Date(status.checkedAt).toLocaleTimeString('en-GB',
            { timeZone, hour: '2-digit', minute: '2-digit' }) + ')'
        : '';
    if (status.state === 'REACHABLE') {
        return {
            text: 'External Variable Service: connected',
            title: 'The External Variable Service answered' + checked + '.',
            unreachable: false,
        };
    }
    return {
        text: 'External Variable Service: not reachable',
        title: 'The External Variable Service didn\'t answer, or refused the Web Service\'s '
            + 'token' + checked + '. Dialogues run without its values. Admins find the details '
            + 'in Technical Information.',
        unreachable: true,
    };
}
