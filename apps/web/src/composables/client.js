import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';
import { useStateManagement } from './state-management.js';

export function useClient() {
    const config = inject('config');
    const state = inject('state');
    const stateManagement = useStateManagement();
    const client = new DialogueBranchClient(config.baseUrl, state.value.user?.accessToken ?? null);

    let pendingRefresh = null;
    client.onUnauthorized(() => {
        if (!pendingRefresh) {
            pendingRefresh = stateManagement.refreshSession()
                .then(() => state.value.user?.accessToken ?? null)
                .catch(() => null)
                .finally(() => { pendingRefresh = null; });
        }
        return pendingRefresh;
    });

    return client;
}
