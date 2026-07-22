import { inject } from 'vue';
import { DialogueBranchClient } from '../dlb-lib/DialogueBranchClient.js';

let _client = null;

export function useClient() {
    const config = inject('config');

    if (!_client) {
        _client = new DialogueBranchClient(config.baseUrl);
    }

    return _client;
}

export function resetClient() {
    _client = null;
}
