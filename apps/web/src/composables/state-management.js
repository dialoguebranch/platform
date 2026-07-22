import { inject } from 'vue';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';
import { logEvent } from './debug-log.js';
import { useClient, resetClient } from './client.js';
import { redirectToLogout } from '../auth.js';

class StateManagement {
    constructor(stateRef, client) {
        this._stateRef = stateRef;
        this._client = client;
    }

    async logout() {
        const currentUser = this._stateRef.value.user;
        try {
            // Ends the Dialogue Branch Web Service's own server-side UserService session — a
            // separate concept from the BFF's session cookie, ended below.
            await this._client.logout();
        } catch (_) { /* best-effort — proceed with local logout regardless */ }
        logEvent('auth', 'User $1 logged out', currentUser?.name ?? 'unknown');
        resetClient();
        DocumentFunctions.deleteCookie('state.selectedProject');
        this._stateRef.value.selectedProject = null;
        this._stateRef.value.user = null;
        // Ends the BFF's session and the Keycloak SSO session, then redirects back to `/`.
        redirectToLogout();
    }
}

export function useStateManagement() {
    const state = inject('state');
    const client = useClient();
    return new StateManagement(state, client);
}
