/**
 * Turns a rejection from DialogueBranchClient (a `Response` for HTTP errors, or a raw
 * network/fetch error otherwise) into a short, user-facing message.
 */
export function describeError(error) {
    if (typeof Response !== 'undefined' && error instanceof Response) {
        return `Request failed (HTTP ${error.status}${error.statusText ? ' — ' + error.statusText : ''}).`;
    }
    return 'Network error — please check your connection and try again.';
}
