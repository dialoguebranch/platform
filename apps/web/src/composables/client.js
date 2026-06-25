import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';
import { useStateManagement } from './state-management.js';

let _client = null;

export function useClient() {
    const config = inject('config');
    const state = inject('state');
    const stateManagement = useStateManagement();

    if (!_client) {
        _client = new DialogueBranchClient(config.baseUrl, () => state.value.user?.accessToken ?? null);

        let pendingRefresh = null;
        _client.onUnauthorized(() => {
            if (!pendingRefresh) {
                pendingRefresh = stateManagement.refreshSession()
                    .then(() => state.value.user?.accessToken ?? null)
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
