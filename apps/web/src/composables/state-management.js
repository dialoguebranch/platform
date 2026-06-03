import { inject } from 'vue';
import { DocumentFunctions } from '../dlb-lib/util/DocumentFunctions.js';

class StateManagement {
    constructor(stateRef) {
        this._stateRef = stateRef;
    }

    logout() {
        DocumentFunctions.deleteCookie('user.name');
        DocumentFunctions.deleteCookie('user.authToken');
        DocumentFunctions.deleteCookie('user.role');
        this._stateRef.value.user = null;
    }
}

export function useStateManagement() {
    const state = inject('state');
    return new StateManagement(state);
}
