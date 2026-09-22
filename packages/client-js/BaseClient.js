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

import { DialogueBranchError } from "./DialogueBranchError.js";

/**
 * Shared transport + response handling for {@link DialogueBranchClient} (playback) and
 * {@link DialogueBranchAuthoringClient} (authoring) — not part of this package's public contract
 * on its own (not re-exported via `package.json`'s `exports`), since a consumer only ever needs
 * one or both of the two concrete clients, never this base directly.
 */
export class BaseClient {

    /**
     * Transport is injected, not imported, so this client has no upward dependency on any
     * particular app (or on Vue/`document`) and can run in any JS runtime.
     *
     * @param {Object} [options]
     * @param {string} options.baseUrl Base URL of the Dialogue Branch Web Service, e.g.
     * `"https://example.com/dlb-web-service/v1"`. Every request is `baseUrl + <endpoint path>`.
     * @param {typeof fetch} [options.fetch] The `fetch` implementation to use. Defaults to
     * `globalThis.fetch` (bound to `globalThis` — native `fetch` throws `"Illegal invocation"`
     * when called as a plain property instead of a method on `window`/`globalThis`, since it
     * checks its receiver's internal type). Pass your own for a non-browser runtime, testing, or
     * to wrap it (retries, logging).
     * @param {RequestCredentials} [options.credentials] The `fetch` `credentials` mode, applied
     * to every request. Defaults to `'same-origin'`. A cookie-session consumer (like Dialogue
     * Branch Studio, talking to its BFF) sets `'include'`.
     * @param {(url: string, init: RequestInit) => void} [options.onRequest] Called with the
     * request URL and the mutable `fetch` options object just before every request goes out —
     * the seam for attaching auth (a CSRF header, an `Authorization: Bearer` token, …). Runs on
     * every request regardless of method; a caller wanting to restrict something to
     * state-changing methods (e.g. CSRF, which only matters there) does that check itself inside
     * the callback.
     * @param {(method: string, path: string, status: number, responseBody: string|null, requestBody: string|null) => void} [options.onApiCall]
     * Called after every request completes (or fails to reach the server, with `status: 0`) —
     * a hook for logging/debugging. Leaving it unset skips an internal body-read-and-reconstruct
     * step entirely, so there's a small performance/correctness benefit (it would otherwise risk
     * corrupting a binary response body) to only setting it when you actually want the logging.
     * @param {() => void} [options.onUnauthorized] Called when a request gets a `401` response —
     * typically used to redirect to a login page. If provided, the method call that triggered it
     * returns a promise that never resolves (on the assumption a navigation is about to happen).
     * If not provided, the method call instead rejects with a normal `{status: 401, ...}` error
     * object, so a consumer with no hook still finds out the call failed rather than hanging.
     */
    constructor({ baseUrl, fetch = globalThis.fetch.bind(globalThis), credentials = 'same-origin', onRequest, onApiCall, onUnauthorized } = {}) {
        this._baseUrl = baseUrl;
        this._fetchImpl = fetch;
        this._credentials = credentials;
        this._onRequest = onRequest;
        this._onApiCall = onApiCall;
        this._onUnauthorized = onUnauthorized;
        this.delegateUser = null;
    }

    get _delegateParam() {
        return this.delegateUser ? '&delegateUser=' + encodeURIComponent(this.delegateUser) : '';
    }

    // Attaches credentials to every call via the injected `_credentials` mode, and gives
    // `_onRequest` a chance to mutate headers before the call goes out (e.g. Studio attaches its
    // CSRF header there — that restriction to state-changing methods is CSRF-specific, so it
    // lives at the call site, not here; `_onRequest` itself fires on every request).
    async _fetch(url, options, logRequestBody = null) {
        const method = (options?.method || 'GET').toUpperCase();
        const path = url.startsWith(this._baseUrl) ? url.slice(this._baseUrl.length) : url;

        const fetchOptions = { ...options, credentials: this._credentials };
        this._onRequest?.(url, fetchOptions);

        let response;
        try {
            response = await this._fetchImpl(url, fetchOptions);
        } catch (networkError) {
            this._onApiCall?.(method, path, 0, null, logRequestBody);
            throw networkError;
        }
        if (!this._onApiCall) {
            return response;
        }
        // Debug-log capture reads the body as text and reconstructs a new Response from that
        // string — skipped entirely above when nothing is listening, since that reconstruction
        // would corrupt binary content and isn't free.
        const text = await response.text().catch(() => null);
        this._onApiCall(method, path, response.status, text, logRequestBody);
        const nullBodyStatus = [204, 205, 304].includes(response.status);
        return new Response(nullBodyStatus ? null : text, {
            status: response.status,
            statusText: response.statusText,
            headers: response.headers,
        });
    }

    // Shared 401 handling. With an `onUnauthorized` hook (Studio's real-navigation login
    // redirect), returns a promise that never resolves so callers' .then()/.catch() don't fire
    // while that navigation is in flight. Without one, rejects with a normal error instead of
    // hanging forever — a consumer with no hook still needs to find out the call failed.
    _unauthorizedResult() {
        if (this._onUnauthorized) {
            this._onUnauthorized();
            return new Promise(() => {});
        }
        return Promise.reject(new DialogueBranchError('Unauthorized', {
            status: 401,
            statusText: 'Unauthorized',
        }));
    }

    _handleResponse(response) {
        if (response.status === 401) {
            return this._unauthorizedResult();
        }
        if (response.ok) {
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.startsWith('application/json')) {
                // A 2xx response can still have an empty/malformed body (e.g. truncated by a
                // proxy) — without this catch, response.json()'s rejection has no `status` field
                // and describeError() would mislabel it as a generic network error.
                return response.json().catch(() => Promise.reject(new DialogueBranchError(
                    'The server returned an invalid response.',
                    { status: response.status, statusText: response.statusText },
                )));
            } else {
                return response.text();
            }
        }
        // Error responses are JSON HttpError bodies (code, message, fieldErrors, and — for some
        // errors, e.g. a project that fails to parse — a structured "errors" field mirroring
        // /publish/verify's shape). Parse it so callers (see error-message.js) can show the
        // actual backend message instead of just the HTTP status.
        return response.json()
            .catch(() => null)
            .then((body) => Promise.reject(new DialogueBranchError(
                body?.message ?? `The server returned an error (${response.status}).`,
                {
                    status: response.status,
                    statusText: response.statusText,
                    code: body?.code ?? null,
                    fieldErrors: body?.fieldErrors ?? [],
                    errors: body?.errors ?? null,
                },
            )));
    }
}
