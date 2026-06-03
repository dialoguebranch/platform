import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';
import { useStateManagement } from './state-management.js';

export function useClient() {
    const config = inject('config');
    const state = inject('state');
    const stateManagement = useStateManagement();
    const client = new DialogueBranchClient(config.baseUrl, state.value.user?.accessToken ?? null);
    client.onUnauthorized((response) => {
        // the token must have become invalid
        stateManagement.logout();
    });
    return client;
}
