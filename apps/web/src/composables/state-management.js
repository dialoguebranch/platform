import { inject } from 'vue';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';
import { logEvent } from './debug-log.js';
import { useClient, resetClient } from './client.js';

class StateManagement {
    constructor(stateRef, keycloak, client) {
        this._stateRef = stateRef;
        this._keycloak = keycloak;
        this._client = client;
    }

    async logout() {
        const currentUser = this._stateRef.value.user;
        try {
            // Reuse the shared client so this goes out with a live token (refreshed via
            // onUnauthorized if needed) rather than the access token captured at login,
            // which may well have expired by the time the user actually logs out.
            await this._client.logout();
        } catch (_) { /* best-effort — proceed with local logout regardless */ }
        logEvent('auth', 'User $1 logged out', currentUser?.name ?? 'unknown');
        resetClient();
        DocumentFunctions.deleteCookie('state.selectedProject');
        this._stateRef.value.selectedProject = null;
        this._stateRef.value.user = null;
        await this._keycloak.logout({ redirectUri: window.location.origin });
    }
}

export function useStateManagement() {
    const state = inject('state');
    const keycloak = inject('keycloak');
    const client = useClient();
    return new StateManagement(state, keycloak, client);
}
