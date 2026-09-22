import { inject } from 'vue';
import { DialogueBranchClient } from '@dialoguebranch/client-js';
import { DialogueBranchAuthoringClient } from '@dialoguebranch/client-js/authoring';
import { logApiCall } from './debug-log.js';
import { redirectToLogin } from '../auth.js';
import { DocumentFunctions } from '../authoring/DocumentFunctions.js';

let _client = null;
let _authoringClient = null;

// DialogueBranchClient no longer infers this itself (see #252) — the caller decides. Studio
// wants the browser's own time zone, same as the client previously computed internally.
export function getBrowserTimeZone() {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

// Shared transport options for both clients — identical wiring, just two different classes (see
// #231: playback and authoring were split into DialogueBranchClient / DialogueBranchAuthoringClient
// so an external playback-only consumer isn't stuck importing Studio's whole admin surface).
function clientOptions(config) {
    return {
        baseUrl: config.baseUrl,
        credentials: 'include',
        // CSRF header only matters to Spring Security's filter on state-changing methods —
        // that restriction is CSRF-specific, so it's applied here rather than inside the
        // client, which calls onRequest on every request regardless of method.
        onRequest: (url, init) => {
            const method = (init.method || 'GET').toUpperCase();
            if (['GET', 'HEAD'].includes(method)) return;
            const csrfToken = DocumentFunctions.getCookie('XSRF-TOKEN');
            if (csrfToken) {
                init.headers = { ...init.headers, 'X-XSRF-TOKEN': csrfToken };
            }
        },
        onApiCall: logApiCall,
        onUnauthorized: redirectToLogin,
    };
}

export function useClient() {
    const config = inject('config');

    if (!_client) {
        _client = new DialogueBranchClient(clientOptions(config));
    }

    return _client;
}

export function useAuthoringClient() {
    const config = inject('config');

    if (!_authoringClient) {
        _authoringClient = new DialogueBranchAuthoringClient(clientOptions(config));
    }

    return _authoringClient;
}

// The playback and authoring clients are separate instances, so `delegateUser` (used to test/run
// dialogues "as" another user) has to be set on both — a draft test (authoring) and real dialogue
// playback both need to honor it. Studio always goes through this rather than setting
// `.delegateUser` on either client directly.
export function setDelegateUser(subject) {
    if (_client) _client.delegateUser = subject;
    if (_authoringClient) _authoringClient.delegateUser = subject;
}

export function resetClient() {
    _client = null;
    _authoringClient = null;
}
