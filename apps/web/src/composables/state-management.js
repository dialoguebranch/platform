import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';
import { User } from '../dlb-lib/model/User.js';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';
import { logEvent } from './debug-log.js';
import { resetClient } from './client.js';

class StateManagement {
    constructor(stateRef, config) {
        this._stateRef = stateRef;
        this._config = config;
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
        DocumentFunctions.deleteCookie('user.name');
        DocumentFunctions.deleteCookie('user.roles');
        DocumentFunctions.deleteCookie('user.accessToken');
        DocumentFunctions.deleteCookie('user.accessTokenExpiresIn');
        DocumentFunctions.deleteCookie('user.refreshToken');
        DocumentFunctions.deleteCookie('user.refreshTokenExpiresIn');
        DocumentFunctions.deleteCookie('state.selectedProject');
        this._stateRef.value.user = null;
        this._stateRef.value.selectedProject = null;
    }

    refreshSession() {
        const currentUser = this._stateRef.value.user;
        if (!currentUser) return Promise.reject('No user logged in');

        logEvent('auth', 'Token refresh triggered ($1 seconds remaining)', Math.round(currentUser.accessTokenSecondsToLive));
        const client = new DialogueBranchClient(this._config.baseUrl, null);
        return client.refresh(currentUser.refreshToken)
            .then((json) => {
                logEvent('auth', 'Token refresh succeeded');
                const user = new User(
                    currentUser.name,
                    currentUser.roles,
                    json.accessToken,
                    json.expiresIn,
                    json.refreshToken,
                    json.refreshExpiresIn
                );
                DocumentFunctions.setCookie('user.accessToken', user.accessToken, null);
                DocumentFunctions.setCookie('user.accessTokenExpiresIn', user.accessTokenExpirationSeconds, null);
                DocumentFunctions.setCookie('user.refreshToken', user.refreshToken, null);
                DocumentFunctions.setCookie('user.refreshTokenExpiresIn', user.refreshTokenExpirationSeconds, null);
                this._stateRef.value.user = user;
            })
            .catch(() => {
                logEvent('auth', 'Token refresh failed — logging out');
                this.logout();
            });
    }
}

export function useStateManagement() {
    const state = inject('state');
    const config = inject('config');
    return new StateManagement(state, config);
}
