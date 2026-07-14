/**
 * Turns a rejection from DialogueBranchClient (a parsed API error — see
 * DialogueBranchClient._handleResponse() — or a raw network/fetch error otherwise) into a
 * short, user-facing message.
 */
export function describeError(error) {
    if (error && typeof error === 'object' && 'status' in error) {
        if (error.message) return error.message;
        return `Request failed (HTTP ${error.status}${error.statusText ? ' — ' + error.statusText : ''}).`;
    }
    return 'Network error — please check your connection and try again.';
}
