import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';

let _client = null;

export function useClient() {
    const config = inject('config');
    const keycloak = inject('keycloak');

    if (!_client) {
        _client = new DialogueBranchClient(config.baseUrl, () => keycloak.token ?? null);

        let pendingRefresh = null;
        _client.onUnauthorized(() => {
            if (!pendingRefresh) {
                pendingRefresh = keycloak.updateToken(30)
                    .then(() => keycloak.token ?? null)
                    .catch(() => null)
                    .finally(() => { pendingRefresh = null; });
            }
            return pendingRefresh;
        });
    }

    return _client;
}

export function resetClient() {
    _client = null;
}
