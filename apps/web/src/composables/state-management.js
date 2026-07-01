import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';
import { logEvent } from './debug-log.js';
import { resetClient } from './client.js';

class StateManagement {
    constructor(stateRef, config, keycloak) {
        this._stateRef = stateRef;
        this._config = config;
        this._keycloak = keycloak;
    }

    async logout() {
        const currentUser = this._stateRef.value.user;
        if (currentUser?.accessToken) {
            try {
                const client = new DialogueBranchClient(this._config.baseUrl, currentUser.accessToken);
                await client.logout();
            } catch (_) { /* best-effort — proceed with local logout regardless */ }
        }
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
    const config = inject('config');
    const keycloak = inject('keycloak');
    return new StateManagement(state, config, keycloak);
}
